package com.xuejiai.aaf.framework.task.queue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.engine.meta.runtime.TaskExecutionInProgressException;
import com.xuejiai.aaf.framework.task.TaskProperties;

import lombok.RequiredArgsConstructor;

/** 队列任务 inbox 的持久 claim、完成和释放状态机。 */
@Component
@RequiredArgsConstructor
public class TaskInboxStateService {

    private static final String PENDING = "PENDING";
    private static final String PROCESSING = "PROCESSING";
    private static final String COMPLETED = "COMPLETED";

    private final JdbcTemplate jdbcTemplate;
    private final TaskProperties taskProperties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InboxClaim claim(String taskId, String taskType, String payloadDigest) {
        var now = Instant.now();
        var leaseToken = UUID.randomUUID();
        var leaseUntil = now.plus(taskProperties.getQueue().getProcessingLease());
        var inserted =
                jdbcTemplate.update(
                        """
                INSERT INTO sys_task_inbox
                    (task_id, task_type, payload_digest, status, lease_token, lease_until)
                VALUES (?, ?, ?, 'PROCESSING', ?, ?)
                ON CONFLICT (task_id) DO NOTHING
                """,
                        taskId,
                        taskType,
                        payloadDigest,
                        leaseToken,
                        Timestamp.from(leaseUntil));
        if (inserted == 1) {
            return InboxClaim.processing(taskId, leaseToken);
        }
        var existing =
                Objects.requireNonNull(
                        jdbcTemplate.queryForObject(
                                """
                SELECT task_type, payload_digest, status, result, lease_until
                FROM sys_task_inbox WHERE task_id = ? FOR UPDATE
                """,
                                (rs, rowNum) ->
                                        new InboxRecord(
                                                rs.getString("task_type"),
                                                rs.getString("payload_digest"),
                                                rs.getString("status"),
                                                rs.getString("result"),
                                                rs.getTimestamp("lease_until") == null
                                                        ? null
                                                        : rs.getTimestamp("lease_until")
                                                                .toInstant()),
                                taskId));
        requireSameTask(existing, taskType, payloadDigest, taskId);
        if (COMPLETED.equals(existing.status())) {
            return InboxClaim.completed(existing.result());
        }
        if (PROCESSING.equals(existing.status())
                && existing.leaseUntil() != null
                && existing.leaseUntil().isAfter(now)) {
            throw new TaskExecutionInProgressException("任务正在由其他消费者处理: " + taskId);
        }
        if (!PENDING.equals(existing.status()) && !PROCESSING.equals(existing.status())) {
            throw new IllegalStateException("任务 inbox 状态非法: " + existing.status());
        }
        var claimed =
                jdbcTemplate.update(
                        """
                UPDATE sys_task_inbox
                SET status = 'PROCESSING', lease_token = ?, lease_until = ?,
                    completed_at = NULL, result = NULL
                WHERE task_id = ? AND (status = 'PENDING'
                    OR (status = 'PROCESSING' AND lease_until <= ?))
                """,
                        leaseToken,
                        Timestamp.from(leaseUntil),
                        taskId,
                        Timestamp.from(now));
        if (claimed != 1) {
            throw new TaskExecutionInProgressException("任务正在由其他消费者处理: " + taskId);
        }
        return InboxClaim.processing(taskId, leaseToken);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(InboxClaim claim, String result) {
        var completed =
                jdbcTemplate.update(
                        """
                UPDATE sys_task_inbox
                SET status = 'COMPLETED', result = ?, completed_at = CURRENT_TIMESTAMP,
                    lease_token = NULL, lease_until = NULL
                WHERE task_id = ? AND status = 'PROCESSING' AND lease_token = ?
                """,
                        result,
                        claim.taskId(),
                        claim.leaseToken());
        if (completed != 1) {
            throw new TaskExecutionInProgressException("任务 inbox 租约已失效: " + claim.taskId());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(InboxClaim claim) {
        jdbcTemplate.update(
                """
                UPDATE sys_task_inbox SET status = 'PENDING', lease_token = NULL, lease_until = NULL
                WHERE task_id = ? AND status = 'PROCESSING' AND lease_token = ?
                """,
                claim.taskId(),
                claim.leaseToken());
    }

    private void requireSameTask(
            InboxRecord existing, String taskType, String payloadDigest, String taskId) {
        if (!taskType.equals(existing.taskType())
                || !payloadDigest.equals(existing.payloadDigest())) {
            throw new IllegalStateException("任务 ID 已被不同类型或载荷占用: " + taskId);
        }
    }

    public record InboxClaim(String taskId, UUID leaseToken, String result) {
        static InboxClaim processing(String taskId, UUID leaseToken) {
            return new InboxClaim(taskId, leaseToken, null);
        }

        static InboxClaim completed(String result) {
            return new InboxClaim(null, null, result);
        }

        public boolean completed() {
            return leaseToken == null;
        }
    }

    private record InboxRecord(
            String taskType,
            String payloadDigest,
            String status,
            String result,
            Instant leaseUntil) {}
}
