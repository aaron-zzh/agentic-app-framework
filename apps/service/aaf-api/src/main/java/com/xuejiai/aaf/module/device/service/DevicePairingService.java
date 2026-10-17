package com.xuejiai.aaf.module.device.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.device.ErrorCodeConstants.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.enums.device.DeviceStatusEnum;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.common.util.ServletUtils;
import com.xuejiai.aaf.module.device.domain.Device;
import com.xuejiai.aaf.module.device.mqtt.MqttConnectionConfig;
import com.xuejiai.aaf.module.device.repository.DeviceRepository;
import com.xuejiai.aaf.module.device.vo.DevicePairingVO;

/** 匿名设备配对会话。Redis 仅保存十分钟临时态，设备事实源仍是 PostgreSQL。 */
@Service
public class DevicePairingService {

    private static final Duration SESSION_TTL = Duration.ofMinutes(10);
    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);
    private static final int SERIAL_START_LIMIT = 5;
    private static final int MAX_BIND_FAILURES = 5;
    private static final String PAIR_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String SESSION_PREFIX = "device:pair:session:";
    private static final String CODE_PREFIX = "device:pair:code:";
    private static final String FAILURE_PREFIX = "device:pair:failure:";
    private static final String SERIAL_RATE_PREFIX = "device:pair:start:serial:";

    private final StringRedisTemplate redisTemplate;
    private final DeviceRepository deviceRepository;
    private final MqttConnectionConfig mqttConfig;

    public DevicePairingService(
            StringRedisTemplate redisTemplate,
            DeviceRepository deviceRepository,
            MqttConnectionConfig mqttConfig) {
        this.redisTemplate = redisTemplate;
        this.deviceRepository = deviceRepository;
        this.mqttConfig = mqttConfig;
    }

    public DevicePairingVO.StartResponse start(
            String serialNumber, String model, String firmwareVersion) {
        var normalizedSerial = requireText(serialNumber, DEVICE_PAIRING_REQUEST_INVALID);
        enforceSerialStartRate(normalizedSerial);
        if (deviceRepository.existsBySerialNumberAndStatus(
                normalizedSerial, DeviceStatusEnum.BOUND.getCode())) {
            throw exception(DEVICE_ALREADY_BOUND);
        }

        var deviceCode = randomDeviceCode();
        var pairCode = reservePairCode(deviceCode);
        var expiresAt = LocalDateTime.now().plus(SESSION_TTL);
        var session =
                new PairingSession(
                        deviceCode,
                        normalizedSerial,
                        trimToNull(model),
                        trimToNull(firmwareVersion),
                        "PENDING",
                        null,
                        null,
                        null);
        redisTemplate
                .opsForValue()
                .set(sessionKey(deviceCode), JsonUtils.toJsonString(session), SESSION_TTL);
        var qrContent = "aaf://device/pair?pairCode=%s".formatted(pairCode);
        return new DevicePairingVO.StartResponse(pairCode, deviceCode, expiresAt, qrContent);
    }

    /** 创建实体前只读校验，真正的单次消费在 {@link #claimForBinding(String)} 完成。 */
    public PendingPairing requirePending(String pairCode) {
        requireBindingSourceNotLocked();
        var normalizedCode = normalizePairCode(pairCode);
        var deviceCode = redisTemplate.opsForValue().get(codeKey(normalizedCode));
        var session = readSession(deviceCode);
        if (session == null || !"PENDING".equals(session.status())) {
            recordBindingFailure();
        }
        return new PendingPairing(
                normalizedCode,
                session.deviceCode(),
                session.serialNumber(),
                session.model(),
                session.firmwareVersion());
    }

    /** 原子删除配对码映射；并发绑定时仅一个请求能取得 claim。 */
    public PairingClaim claimForBinding(String pairCode) {
        requireBindingSourceNotLocked();
        var normalizedCode = normalizePairCode(pairCode);
        var deviceCode = redisTemplate.opsForValue().getAndDelete(codeKey(normalizedCode));
        var session = readSession(deviceCode);
        if (session == null || !"PENDING".equals(session.status())) {
            recordBindingFailure();
        }
        redisTemplate.delete(failureKey());
        return new PairingClaim(normalizedCode, session);
    }

    /** 数据库事务回滚时恢复仍有效的配对码，避免瞬时故障消耗设备会话。 */
    public void restoreClaim(PairingClaim claim) {
        var ttlMillis = remainingMillis(sessionKey(claim.session().deviceCode()));
        if (ttlMillis <= 0 || readSession(claim.session().deviceCode()) == null) {
            return;
        }
        redisTemplate
                .opsForValue()
                .setIfAbsent(
                        codeKey(claim.pairCode()),
                        claim.session().deviceCode(),
                        Duration.ofMillis(ttlMillis));
    }

    /** 数据库提交后发布一次性设备密钥，保持原配对会话的十分钟期限。 */
    public void completeBinding(PairingClaim claim, Device device, String plaintextCredential) {
        var key = sessionKey(claim.session().deviceCode());
        var ttlMillis = remainingMillis(key);
        if (ttlMillis <= 0) {
            throw exception(DEVICE_PAIRING_EXPIRED);
        }
        var bound =
                new PairingSession(
                        claim.session().deviceCode(),
                        claim.session().serialNumber(),
                        claim.session().model(),
                        claim.session().firmwareVersion(),
                        "BOUND",
                        device.getDeviceKey(),
                        plaintextCredential,
                        device.getId());
        redisTemplate
                .opsForValue()
                .set(key, JsonUtils.toJsonString(bound), Duration.ofMillis(ttlMillis));
    }

    /** BOUND 会话通过 getAndDelete 保证密钥最多返回一次，随后只保留无密钥交付标记。 */
    public DevicePairingVO.PollResponse poll(String deviceCode, String apiBaseUrl) {
        var normalizedDeviceCode = requireText(deviceCode, DEVICE_PAIRING_REQUEST_INVALID);
        var key = sessionKey(normalizedDeviceCode);
        var session = readSession(normalizedDeviceCode);
        if (session == null) {
            throw exception(DEVICE_PAIRING_EXPIRED);
        }
        if ("PENDING".equals(session.status())) {
            return DevicePairingVO.PollResponse.pending();
        }
        if ("DELIVERED".equals(session.status())) {
            return DevicePairingVO.PollResponse.delivered();
        }
        if (!"BOUND".equals(session.status())) {
            throw exception(DEVICE_PAIRING_REQUEST_INVALID);
        }

        var ttlMillis = remainingMillis(key);
        var claimedJson = redisTemplate.opsForValue().getAndDelete(key);
        var claimed = JsonUtils.parseObjectQuietly(claimedJson, PairingSession.class);
        if (claimed == null || !"BOUND".equals(claimed.status())) {
            return DevicePairingVO.PollResponse.delivered();
        }
        if (ttlMillis > 0) {
            var delivered =
                    new PairingSession(
                            claimed.deviceCode(),
                            claimed.serialNumber(),
                            claimed.model(),
                            claimed.firmwareVersion(),
                            "DELIVERED",
                            claimed.deviceKey(),
                            null,
                            claimed.deviceId());
            redisTemplate
                    .opsForValue()
                    .set(key, JsonUtils.toJsonString(delivered), Duration.ofMillis(ttlMillis));
        }
        return new DevicePairingVO.PollResponse(
                "BOUND",
                claimed.deviceKey(),
                claimed.plaintextCredential(),
                mqttAccess(claimed.deviceKey()),
                apiBaseUrl);
    }

    private DevicePairingVO.MqttAccess mqttAccess(String deviceKey) {
        return new DevicePairingVO.MqttAccess(
                mqttConfig.host(),
                mqttConfig.port(),
                true,
                deviceKey,
                deviceKey,
                mqttConfig.tenantKey(),
                mqttConfig.keepAliveSeconds(),
                mqttConfig.sessionExpirySeconds());
    }

    private void enforceSerialStartRate(String serialNumber) {
        var key = SERIAL_RATE_PREFIX + sha256Hex(serialNumber);
        var count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, RATE_WINDOW);
        }
        if (count != null && count > SERIAL_START_LIMIT) {
            throw exception(DEVICE_PAIRING_RATE_LIMITED);
        }
    }

    private String reservePairCode(String deviceCode) {
        for (var attempt = 0; attempt < 20; attempt++) {
            var pairCode = randomPairCode();
            var reserved =
                    redisTemplate
                            .opsForValue()
                            .setIfAbsent(codeKey(pairCode), deviceCode, SESSION_TTL);
            if (Boolean.TRUE.equals(reserved)) {
                return pairCode;
            }
        }
        throw new IllegalStateException("无法分配设备配对码");
    }

    private void requireBindingSourceNotLocked() {
        var value = redisTemplate.opsForValue().get(failureKey());
        if (value != null && parseCount(value) >= MAX_BIND_FAILURES) {
            throw exception(DEVICE_PAIRING_LOCKED);
        }
    }

    private void recordBindingFailure() {
        var key = failureKey();
        var count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, SESSION_TTL);
        }
        if (count != null && count >= MAX_BIND_FAILURES) {
            throw exception(DEVICE_PAIRING_LOCKED);
        }
        throw exception(DEVICE_PAIRING_INVALID);
    }

    private PairingSession readSession(String deviceCode) {
        if (deviceCode == null || deviceCode.isBlank()) {
            return null;
        }
        return JsonUtils.parseObjectQuietly(
                redisTemplate.opsForValue().get(sessionKey(deviceCode)), PairingSession.class);
    }

    private long remainingMillis(String key) {
        var value = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        return value == null ? -1 : value;
    }

    private String failureKey() {
        var source = ServletUtils.getClientIp();
        return FAILURE_PREFIX + sha256Hex(source == null ? "unknown" : source);
    }

    private static String sessionKey(String deviceCode) {
        return SESSION_PREFIX + sha256Hex(deviceCode);
    }

    private static String codeKey(String pairCode) {
        return CODE_PREFIX + sha256Hex(pairCode);
    }

    private static String randomPairCode() {
        var code = new StringBuilder(8);
        for (var index = 0; index < 8; index++) {
            code.append(PAIR_ALPHABET.charAt(SECURE_RANDOM.nextInt(PAIR_ALPHABET.length())));
        }
        return code.toString();
    }

    private static String randomDeviceCode() {
        var bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizePairCode(String pairCode) {
        if (pairCode == null || pairCode.isBlank()) {
            recordBindingFailure();
        }
        var value = pairCode.trim().toUpperCase(Locale.ROOT);
        if (value.length() != 8) {
            recordBindingFailure();
        }
        return value;
    }

    private static String requireText(
            String value, com.xuejiai.aaf.common.exception.ErrorCode code) {
        if (value == null || value.isBlank()) {
            throw exception(code);
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int parseCount(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return MAX_BIND_FAILURES;
        }
    }

    private static String sha256Hex(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    public record PendingPairing(
            String pairCode,
            String deviceCode,
            String serialNumber,
            String model,
            String firmwareVersion) {}

    public record PairingClaim(String pairCode, PairingSession session) {}

    public record PairingSession(
            String deviceCode,
            String serialNumber,
            String model,
            String firmwareVersion,
            String status,
            String deviceKey,
            String plaintextCredential,
            Long deviceId) {}
}
