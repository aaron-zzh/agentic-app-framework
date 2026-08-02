package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.core.AiUsage;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;

/** 知识入库 AI 调用的预留与计费端口。 */
public interface KnowledgeUsagePort {

    void precheck(BillingContext billing, String capability, long estimatedCost);

    InvocationReservation reserve(
            BillingContext billing, String stage, String unitKey, String inputDigest);

    boolean start(InvocationReservation reservation);

    void persistProviderResult(
            InvocationReservation reservation, String providerResult, String providerRequestId);

    void markUnknown(InvocationReservation reservation, String errorMessage);

    Optional<StoredProviderResult> findProviderResult(
            BillingContext billing, String stage, String unitKey, String inputDigest);

    AiCreditGuard.IdempotentSettlementResult settle(UsageCall call);

    record BillingContext(String tenantId, UUID runId, Long payerUserId) {
        public BillingContext {
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId 不能为空");
            }
            if (runId == null || payerUserId == null || payerUserId <= 0) {
                throw new IllegalArgumentException("runId 和 payerUserId 必须有效");
            }
        }
    }

    enum ReservationState {
        RESERVED,
        BUSY,
        RECOVERABLE,
        COMPLETED,
        UNKNOWN
    }

    record InvocationReservation(
            BillingContext billing,
            String stage,
            String unitKey,
            String inputDigest,
            String invocationId,
            String usageKey,
            Instant occurredAt,
            UUID ownerToken,
            ReservationState state,
            String providerResult,
            String providerRequestId) {}

    record StoredProviderResult(
            String invocationId,
            String usageKey,
            String inputDigest,
            String providerResult,
            String providerRequestId,
            Instant occurredAt,
            boolean settled) {}

    record UsageCall(
            String usageKey,
            BillingContext billing,
            String stage,
            String unitKey,
            String invocationId,
            String inputDigest,
            String providerResult,
            String providerRequestId,
            AiModel model,
            AiUsage usage,
            BigDecimal costYuan,
            Instant occurredAt) {}

    static String invocationId(UUID runId, String stage, String unitKey, String inputDigest) {
        return TrustedKnowledgeStore.sha256(
                        "%s|%s|%s|%s".formatted(runId, stage, unitKey, inputDigest))
                .substring(0, 32);
    }

    static String usageKey(UUID runId, String stage, String unitKey, String invocationId) {
        return "knowledge:ingest:%s:%s:%s:%s".formatted(runId, stage, unitKey, invocationId);
    }
}
