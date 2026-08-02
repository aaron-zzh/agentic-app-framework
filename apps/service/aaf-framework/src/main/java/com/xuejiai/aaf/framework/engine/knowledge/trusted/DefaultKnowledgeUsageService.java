package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;

import lombok.RequiredArgsConstructor;

/** KnowledgeUsagePort 的积分系统适配器。 */
@Service
@RequiredArgsConstructor
public class DefaultKnowledgeUsageService implements KnowledgeUsagePort {

    private static final long RESERVATION_LEASE_MINUTES = 5;

    private final AiCreditGuard creditGuard;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void precheck(BillingContext billing, String capability, long estimatedCost) {
        creditGuard.precheck(billing.payerUserId(), capability, estimatedCost);
    }

    @Override
    public InvocationReservation reserve(
            BillingContext billing, String stage, String unitKey, String inputDigest) {
        var invocationId =
                KnowledgeUsagePort.invocationId(billing.runId(), stage, unitKey, inputDigest);
        var usageKey = KnowledgeUsagePort.usageKey(billing.runId(), stage, unitKey, invocationId);
        var occurredAt = Instant.now();
        var ownerToken = UUID.randomUUID();
        var leaseUntil = occurredAt.plus(RESERVATION_LEASE_MINUTES, ChronoUnit.MINUTES);
        var inserted =
                jdbcTemplate.update(
                        """
                        INSERT INTO ai_knowledge_ingest_unit
                            (run_id, stage, unit_key, input_digest, invocation_id, usage_key,
                             status, occurred_at, reservation_token, lease_until)
                        VALUES (?, ?, ?, ?, ?, ?, 'RESERVED', ?, ?, ?)
                        ON CONFLICT (run_id, stage, unit_key) DO NOTHING
                        """,
                        billing.runId(),
                        stage,
                        unitKey,
                        inputDigest,
                        invocationId,
                        usageKey,
                        occurredAt,
                        ownerToken,
                        leaseUntil);
        if (inserted == 1) {
            return new InvocationReservation(
                    billing,
                    stage,
                    unitKey,
                    inputDigest,
                    invocationId,
                    usageKey,
                    occurredAt,
                    ownerToken,
                    ReservationState.RESERVED,
                    null,
                    invocationId);
        }

        var stored = requireUnit(billing, stage, unitKey, inputDigest);
        var existing = existingReservation(billing, stage, unitKey, stored);
        if (existing != null) {
            return existing;
        }
        if (stored.leaseUntil() != null && stored.leaseUntil().isBefore(Instant.now())) {
            if ("RESERVED".equals(stored.status())) {
                var recovered =
                        jdbcTemplate.update(
                                """
                                UPDATE ai_knowledge_ingest_unit
                                SET reservation_token = ?, lease_until = ?, update_time = CURRENT_TIMESTAMP
                                WHERE run_id = ? AND stage = ? AND unit_key = ?
                                  AND input_digest = ? AND status = 'RESERVED'
                                  AND lease_until < CURRENT_TIMESTAMP
                                """,
                                ownerToken,
                                leaseUntil,
                                billing.runId(),
                                stage,
                                unitKey,
                                inputDigest);
                if (recovered == 1) {
                    return new InvocationReservation(
                            billing,
                            stage,
                            unitKey,
                            inputDigest,
                            stored.invocationId(),
                            stored.usageKey(),
                            stored.occurredAt(),
                            ownerToken,
                            ReservationState.RESERVED,
                            null,
                            stored.providerRequestId());
                }
            } else if ("IN_PROGRESS".equals(stored.status())) {
                var markedUnknown =
                        jdbcTemplate.update(
                                """
                                UPDATE ai_knowledge_ingest_unit
                                SET status = 'UNKNOWN', error_message = ?, lease_until = NULL,
                                    update_time = CURRENT_TIMESTAMP
                                WHERE run_id = ? AND stage = ? AND unit_key = ?
                                  AND input_digest = ? AND status = 'IN_PROGRESS'
                                  AND lease_until < CURRENT_TIMESTAMP
                                """,
                                "调用者失联，供应商结果未知；需按 request id 人工确认后恢复",
                                billing.runId(),
                                stage,
                                unitKey,
                                inputDigest);
                if (markedUnknown == 1) {
                    return reservation(billing, stage, unitKey, stored, ReservationState.UNKNOWN);
                }
                var latest = requireUnit(billing, stage, unitKey, inputDigest);
                var latestReservation = existingReservation(billing, stage, unitKey, latest);
                return latestReservation != null
                        ? latestReservation
                        : reservation(billing, stage, unitKey, latest, ReservationState.BUSY);
            }
        }
        return reservation(billing, stage, unitKey, stored, ReservationState.BUSY);
    }

    @Override
    public boolean start(InvocationReservation reservation) {
        if (reservation.state() != ReservationState.RESERVED || reservation.ownerToken() == null) {
            return false;
        }
        return jdbcTemplate.update(
                        """
                        UPDATE ai_knowledge_ingest_unit
                        SET status = 'IN_PROGRESS', provider_request_id = ?,
                            lease_until = ?, update_time = CURRENT_TIMESTAMP
                        WHERE run_id = ? AND stage = ? AND unit_key = ?
                          AND input_digest = ? AND status = 'RESERVED'
                          AND reservation_token = ?
                        """,
                        reservation.invocationId(),
                        Instant.now().plus(RESERVATION_LEASE_MINUTES, ChronoUnit.MINUTES),
                        reservation.billing().runId(),
                        reservation.stage(),
                        reservation.unitKey(),
                        reservation.inputDigest(),
                        reservation.ownerToken())
                == 1;
    }

    @Override
    public void persistProviderResult(
            InvocationReservation reservation, String providerResult, String providerRequestId) {
        var updated =
                jdbcTemplate.update(
                        """
                        UPDATE ai_knowledge_ingest_unit
                        SET output_json = CAST(? AS jsonb),
                            provider_request_id = COALESCE(?, provider_request_id),
                            lease_until = NULL, error_message = NULL,
                            update_time = CURRENT_TIMESTAMP
                        WHERE run_id = ? AND stage = ? AND unit_key = ?
                          AND input_digest = ? AND invocation_id = ? AND usage_key = ?
                          AND status = 'IN_PROGRESS' AND reservation_token = ?
                        """,
                        providerResult,
                        providerRequestId,
                        reservation.billing().runId(),
                        reservation.stage(),
                        reservation.unitKey(),
                        reservation.inputDigest(),
                        reservation.invocationId(),
                        reservation.usageKey(),
                        reservation.ownerToken());
        if (updated != 1) {
            throw new IllegalStateException("知识入库 provider 结果检查点冲突");
        }
    }

    @Override
    public void markUnknown(InvocationReservation reservation, String errorMessage) {
        jdbcTemplate.update(
                """
                UPDATE ai_knowledge_ingest_unit
                SET status = 'UNKNOWN', error_message = ?, lease_until = NULL,
                    update_time = CURRENT_TIMESTAMP
                WHERE run_id = ? AND stage = ? AND unit_key = ?
                  AND input_digest = ? AND status = 'IN_PROGRESS'
                  AND reservation_token = ? AND output_json IS NULL
                """,
                truncate(errorMessage),
                reservation.billing().runId(),
                reservation.stage(),
                reservation.unitKey(),
                reservation.inputDigest(),
                reservation.ownerToken());
    }

    @Override
    public Optional<StoredProviderResult> findProviderResult(
            BillingContext billing, String stage, String unitKey, String inputDigest) {
        var stored = findUnit(billing, stage, unitKey).stream().findFirst();
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        if (!stored.get().inputDigest().equals(inputDigest)) {
            throw new IllegalStateException("知识入库单元 inputDigest 冲突");
        }
        return stored.get().providerResult() == null
                ? Optional.empty()
                : Optional.of(stored.get().providerResultView());
    }

    @Override
    public AiCreditGuard.IdempotentSettlementResult settle(UsageCall call) {
        var stored = requireUnit(call.billing(), call.stage(), call.unitKey(), call.inputDigest());
        if (stored.providerResult() == null
                || !stored.invocationId().equals(call.invocationId())
                || !stored.usageKey().equals(call.usageKey())) {
            throw new IllegalStateException("知识入库 provider 结果尚未持久化或调用标识冲突");
        }
        var result =
                creditGuard.settleIdempotently(
                        new AiCreditGuard.IdempotentUsageSettlement(
                                stored.usageKey(),
                                call.billing().tenantId(),
                                "knowledge-ingest:" + call.billing().runId(),
                                call.stage() + ":" + call.unitKey(),
                                0,
                                call.billing().payerUserId(),
                                call.model(),
                                call.usage(),
                                call.stage(),
                                call.costYuan(),
                                stored.occurredAt(),
                                "知识入库：" + call.stage()));
        jdbcTemplate.update(
                """
                UPDATE ai_knowledge_ingest_unit
                SET status = 'COMPLETED', reservation_token = NULL,
                    error_message = NULL, update_time = CURRENT_TIMESTAMP
                WHERE run_id = ? AND stage = ? AND unit_key = ?
                  AND input_digest = ? AND invocation_id = ? AND usage_key = ?
                  AND output_json IS NOT NULL
                """,
                call.billing().runId(),
                call.stage(),
                call.unitKey(),
                call.inputDigest(),
                call.invocationId(),
                call.usageKey());
        return result;
    }

    private StoredUnit requireUnit(
            BillingContext billing, String stage, String unitKey, String inputDigest) {
        var stored =
                findUnit(billing, stage, unitKey).stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("知识入库单元未预留"));
        if (!stored.inputDigest().equals(inputDigest)) {
            throw new IllegalStateException("知识入库单元 inputDigest 冲突");
        }
        return stored;
    }

    private List<StoredUnit> findUnit(BillingContext billing, String stage, String unitKey) {
        return jdbcTemplate.query(
                """
                SELECT invocation_id, usage_key, input_digest, output_json::text,
                       provider_request_id, occurred_at, status, reservation_token, lease_until
                FROM ai_knowledge_ingest_unit
                WHERE run_id = ? AND stage = ? AND unit_key = ?
                """,
                (rs, rowNum) ->
                        new StoredUnit(
                                rs.getString(1),
                                rs.getString(2),
                                rs.getString(3),
                                rs.getString(4),
                                rs.getString(5),
                                rs.getTimestamp(6).toInstant(),
                                rs.getString(7),
                                rs.getObject(8, UUID.class),
                                rs.getTimestamp(9) == null ? null : rs.getTimestamp(9).toInstant()),
                billing.runId(),
                stage,
                unitKey);
    }

    private InvocationReservation existingReservation(
            BillingContext billing, String stage, String unitKey, StoredUnit stored) {
        if (stored.providerResult() != null) {
            return reservation(
                    billing,
                    stage,
                    unitKey,
                    stored,
                    stored.settled() ? ReservationState.COMPLETED : ReservationState.RECOVERABLE);
        }
        return "UNKNOWN".equals(stored.status())
                ? reservation(billing, stage, unitKey, stored, ReservationState.UNKNOWN)
                : null;
    }

    private InvocationReservation reservation(
            BillingContext billing,
            String stage,
            String unitKey,
            StoredUnit stored,
            ReservationState state) {
        return new InvocationReservation(
                billing,
                stage,
                unitKey,
                stored.inputDigest(),
                stored.invocationId(),
                stored.usageKey(),
                stored.occurredAt(),
                stored.ownerToken(),
                state,
                stored.providerResult(),
                stored.providerRequestId());
    }

    private String truncate(String value) {
        return value == null || value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    private record StoredUnit(
            String invocationId,
            String usageKey,
            String inputDigest,
            String providerResult,
            String providerRequestId,
            Instant occurredAt,
            String status,
            UUID ownerToken,
            Instant leaseUntil) {

        boolean settled() {
            return "COMPLETED".equals(status);
        }

        StoredProviderResult providerResultView() {
            return new StoredProviderResult(
                    invocationId,
                    usageKey,
                    inputDigest,
                    providerResult,
                    providerRequestId,
                    occurredAt,
                    settled());
        }
    }
}
