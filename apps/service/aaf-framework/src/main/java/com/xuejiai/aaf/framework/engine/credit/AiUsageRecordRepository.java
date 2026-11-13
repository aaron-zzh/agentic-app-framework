package com.xuejiai.aaf.framework.engine.credit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** AI 调用用量记录 Repository。 */
public interface AiUsageRecordRepository extends JpaRepository<AiUsageRecord, Long> {

    Optional<AiUsageRecord> findByUsageKey(String usageKey);

    /**
     * 按用户轮次 runId 汇总已结算积分：覆盖该 run 的直接对话执行、由它升级出的持久任务（协调者、步骤与子智能体共享 taskId）， 以及以 runId 作为归属键的 L0
     * 调用。审批恢复等后续 run 都归入发起它的用户轮次。
     */
    @Query(
            value =
                    """
                    SELECT COALESCE(SUM(r.credit_amount), 0)
                    FROM ai_usage_record r
                    WHERE r.tenant_id = :tenantId
                      AND r.user_id = :userId
                      AND r.credit_tx_id IS NOT NULL
                      AND (r.task_id IN (:runIds)
                        OR r.execution_id IN (
                            SELECT e.execution_id FROM ai_task_execution e
                            WHERE e.org_id = CAST(:tenantId AS BIGINT) AND e.run_id IN (:runIds))
                        OR r.task_id IN (
                            SELECT t.task_id FROM ai_task t
                            WHERE t.org_id = CAST(:tenantId AS BIGINT) AND t.origin_run_id IN (:runIds)))
                    """,
            nativeQuery = true)
    long sumSettledCreditsByRunIds(String tenantId, Long userId, Collection<String> runIds);

    @Modifying
    @Query(
            value =
                    """
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
    @Query(
            """
            UPDATE AiUsageRecord r SET r.creditTxId = :creditTxId
            WHERE r.usageKey = :usageKey AND r.creditTxId IS NULL
            """)
    int completeSettlement(String usageKey, Long creditTxId);
}
