package com.xuejiai.aaf.module.elder.agent.tool;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;

import lombok.RequiredArgsConstructor;

/** 对话写操作的十分钟 Redis 草稿；草稿领取后才允许提交业务 API。 */
@Component
@RequiredArgsConstructor
public class ElderDraftStore {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String KEY_PREFIX = "elder:agent:draft:";
    private static final String MEDICATION = "MEDICATION";
    private static final String SERVICE_ORDER = "SERVICE_ORDER";

    private final StringRedisTemplate redisTemplate;

    public DraftReceipt saveMedication(Long ownerId, Long elderId, MedicationDraft draft) {
        return save(MEDICATION, ownerId, elderId, draft);
    }

    public DraftReceipt saveServiceOrder(Long ownerId, Long elderId, ServiceOrderDraft draft) {
        return save(SERVICE_ORDER, ownerId, elderId, draft);
    }

    public DraftClaim<MedicationDraft> claimMedication(String draftId, Long ownerId, Long elderId) {
        return claim(draftId, MEDICATION, ownerId, elderId, MedicationDraft.class);
    }

    public DraftClaim<ServiceOrderDraft> claimServiceOrder(
            String draftId, Long ownerId, Long elderId) {
        return claim(draftId, SERVICE_ORDER, ownerId, elderId, ServiceOrderDraft.class);
    }

    public void restore(DraftClaim<?> claim) {
        if (claim == null || claim.remainingMillis() <= 0) {
            return;
        }
        redisTemplate
                .opsForValue()
                .setIfAbsent(
                        key(claim.draftId()),
                        claim.rawEnvelope(),
                        Duration.ofMillis(claim.remainingMillis()));
    }

    private DraftReceipt save(String type, Long ownerId, Long elderId, Object payload) {
        Objects.requireNonNull(ownerId, "ownerId 不能为空");
        Objects.requireNonNull(elderId, "elderId 不能为空");
        var draftId = UUID.randomUUID().toString();
        var envelope = new DraftEnvelope(type, ownerId, elderId, JsonUtils.toJsonString(payload));
        redisTemplate.opsForValue().set(key(draftId), JsonUtils.toJsonString(envelope), TTL);
        return new DraftReceipt(draftId, LocalDateTime.now().plus(TTL));
    }

    private <T> DraftClaim<T> claim(
            String draftId, String type, Long ownerId, Long elderId, Class<T> payloadType) {
        if (draftId == null || draftId.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "草稿号不能为空");
        }
        var draftKey = key(draftId.trim());
        var raw = redisTemplate.opsForValue().get(draftKey);
        var envelope = JsonUtils.parseObjectQuietly(raw, DraftEnvelope.class);
        if (envelope == null || !type.equals(envelope.type())) {
            throw new BusinessException(410, "草稿不存在或已过期");
        }
        if (!Objects.equals(ownerId, envelope.ownerId())
                || !Objects.equals(elderId, envelope.elderId())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "草稿不属于当前账号或长者");
        }
        var remaining = redisTemplate.getExpire(draftKey, TimeUnit.MILLISECONDS);
        var claimedRaw = redisTemplate.opsForValue().getAndDelete(draftKey);
        var claimed = JsonUtils.parseObjectQuietly(claimedRaw, DraftEnvelope.class);
        if (claimed == null) {
            throw new BusinessException(410, "草稿不存在、已过期或已确认");
        }
        var payload = JsonUtils.parseObjectQuietly(claimed.payload(), payloadType);
        if (payload == null) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "草稿内容无效");
        }
        return new DraftClaim<>(
                draftId.trim(), payload, claimedRaw, remaining == null ? -1 : remaining);
    }

    private static String key(String draftId) {
        return KEY_PREFIX + draftId;
    }

    public record DraftReceipt(String draftId, LocalDateTime expiresAt) {}

    public record DraftClaim<T>(
            String draftId, T payload, String rawEnvelope, long remainingMillis) {}

    private record DraftEnvelope(String type, Long ownerId, Long elderId, String payload) {}

    public record MedicationDraft(
            String medicationName,
            String dosageInstructions,
            List<String> reminderTimes,
            LocalDate startDate,
            LocalDate endDate) {
        public MedicationDraft {
            reminderTimes = List.copyOf(reminderTimes);
        }
    }

    public record ServiceOrderDraft(
            Long serviceItemId,
            Integer quantity,
            LocalDateTime scheduledAt,
            String serviceAddress,
            String notes) {}
}
