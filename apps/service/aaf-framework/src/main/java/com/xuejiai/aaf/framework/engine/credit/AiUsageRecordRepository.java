package com.xuejiai.aaf.framework.engine.credit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** AI 调用用量记录 Repository。 */
public interface AiUsageRecordRepository extends JpaRepository<AiUsageRecord, Long> {

    Optional<AiUsageRecord> findByUsageKey(String usageKey);

    @Modifying
    @Query(
            value = """
                    INSERT INTO ai_usage_record (
                        usage_key, settlement_digest, tenant_id, task_id, execution_id, fencing_token,
                        occurred_at, user_id, model_id, capability, quota_type,
                        cost_yuan, credit_amount, credit_tx_id, usage, raw_usage, create_time)
                    VALUES (
                        :usageKey, :digest, :tenantId, :taskId, :executionId, :fencingToken,
                        :occurredAt, :userId, :modelId, :capability, :quotaType,
                        :costYuan, :creditAmount, NULL,
                        CAST(:usage AS jsonb), CAST(:rawUsage AS jsonb), CURRENT_TIMESTAMP)
                    ON CONFLICT (usage_key) DO NOTHING
                    """,
            nativeQuery = true)
    int claim(
            String usageKey,
            String digest,
            String tenantId,
            String taskId,
            String executionId,
            long fencingToken,
            Instant occurredAt,
            Long userId,
            Long modelId,
            String capability,
            short quotaType,
            BigDecimal costYuan,
            long creditAmount,
            String usage,
            String rawUsage);

    @Modifying
    @Query("""
            UPDATE AiUsageRecord r SET r.creditTxId = :creditTxId
            WHERE r.usageKey = :usageKey AND r.creditTxId IS NULL
            """)
    int completeSettlement(String usageKey, Long creditTxId);
}
