package com.xuejiai.aaf.module.device.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.device.DeviceStatusEnum;
import com.xuejiai.aaf.module.device.domain.Device;
import com.xuejiai.aaf.module.device.repository.DeviceRepository;
import com.xuejiai.aaf.module.device.security.DevicePrincipal;

/** 设备凭据签发与校验服务。数据库只保存 SHA-256 哈希和展示前缀。 */
@Service
public class DeviceCredentialService {

    private static final String CREDENTIAL_MARKER = "aaf_ed_";
    private static final int LOCATOR_LENGTH = 12;
    private static final int DISPLAY_PREFIX_LENGTH = CREDENTIAL_MARKER.length() + LOCATOR_LENGTH;
    private static final String INVALID_PREFIX = CREDENTIAL_MARKER + "0".repeat(LOCATOR_LENGTH);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String DUMMY_HASH = "0".repeat(64);

    private final DeviceRepository deviceRepository;

    public DeviceCredentialService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    /** 签发一次性明文凭据。调用方不得持久化返回的明文。 */
    @Transactional
    public IssuedCredential issue(Device device) {
        Objects.requireNonNull(device, "device");
        var locator = randomHex(LOCATOR_LENGTH / 2);
        var prefix = CREDENTIAL_MARKER + locator;
        var secret = randomSecret();
        var credential = prefix + "_" + secret;

        device.setCredentialHash(sha256Hex(credential));
        device.setCredentialPrefix(prefix);
        device.setCredentialRevokedAt(null);
        device.setCredentialLastUsedAt(null);
        deviceRepository.save(device);
        return new IssuedCredential(credential, prefix);
    }

    /** 撤销当前凭据并覆盖原哈希，旧凭据后续请求立即失效。 */
    @Transactional
    public void revoke(Device device) {
        Objects.requireNonNull(device, "device");
        device.setCredentialHash(sha256Hex("revoked_" + randomSecret()));
        device.setCredentialRevokedAt(LocalDateTime.now());
        deviceRepository.save(device);
    }

    /** 按设备键校验凭据，哈希和展示前缀均使用常数时间比较。 */
    @Transactional
    public Optional<DevicePrincipal> authenticate(String deviceKey, String credential) {
        var candidateHash = sha256Hex(credential == null ? "" : credential);
        var candidatePrefix = extractPrefix(credential);
        var device =
                deviceKey == null || deviceKey.isBlank()
                        ? Optional.<Device>empty()
                        : deviceRepository.findByDeviceKey(deviceKey);

        if (device.isEmpty()) {
            constantTimeEquals(candidatePrefix, INVALID_PREFIX);
            constantTimeEqualsHex(candidateHash, DUMMY_HASH);
            return Optional.empty();
        }

        var matchedDevice = device.get();
        var prefixMatches =
                constantTimeEquals(candidatePrefix, matchedDevice.getCredentialPrefix());
        var hashMatches = constantTimeEqualsHex(candidateHash, matchedDevice.getCredentialHash());
        var active =
                DeviceStatusEnum.BOUND.getCode().equals(matchedDevice.getStatus())
                        && matchedDevice.getCredentialRevokedAt() == null;
        if (!(prefixMatches & hashMatches) || !active) {
            return Optional.empty();
        }

        matchedDevice.setCredentialLastUsedAt(LocalDateTime.now());
        return Optional.of(
                new DevicePrincipal(
                        matchedDevice.getId(),
                        matchedDevice.getDeviceKey(),
                        matchedDevice.getOwnerUserId(),
                        matchedDevice.getScenarioKey(),
                        matchedDevice.getSubjectId(),
                        matchedDevice.getRoleKey(),
                        matchedDevice.getOrgId(),
                        matchedDevice.getWorkspaceId()));
    }

    private static String randomSecret() {
        var bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String randomHex(int byteLength) {
        var bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String extractPrefix(String credential) {
        if (credential == null
                || credential.length() <= DISPLAY_PREFIX_LENGTH
                || !credential.startsWith(CREDENTIAL_MARKER)
                || credential.charAt(DISPLAY_PREFIX_LENGTH) != '_') {
            return INVALID_PREFIX;
        }
        return credential.substring(0, DISPLAY_PREFIX_LENGTH);
    }

    private static String sha256Hex(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    private static boolean constantTimeEquals(String candidate, String expected) {
        var candidateBytes = (candidate == null ? "" : candidate).getBytes(StandardCharsets.UTF_8);
        var expectedBytes = (expected == null ? "" : expected).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(candidateBytes, expectedBytes);
    }

    private static boolean constantTimeEqualsHex(String candidate, String expected) {
        try {
            return MessageDigest.isEqual(
                    HexFormat.of().parseHex(candidate), HexFormat.of().parseHex(expected));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /** 明文凭据仅在签发时返回一次，prefix 可安全用于管理端展示。 */
    public record IssuedCredential(String plaintextCredential, String prefix) {}
}
