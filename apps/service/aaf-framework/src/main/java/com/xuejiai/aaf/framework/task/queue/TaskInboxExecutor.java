package com.xuejiai.aaf.framework.task.queue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/** 队列任务事务性收件箱，原子提交消费结果与 handler 的关系库副作用。 */
@Component
@RequiredArgsConstructor
public class TaskInboxExecutor {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 在收件箱事务中执行 handler。
     *
     * @return 首次执行或崩溃重投恢复的结果摘要
     */
    @Transactional
    public String execute(
            String taskId, String taskType, String payload, Supplier<String> handler) {
        Objects.requireNonNull(handler, "handler 不能为空");
        var normalizedTaskId = requireText(taskId, "taskId");
        var normalizedTaskType = requireText(taskType, "taskType");
        var payloadDigest = digest(Objects.requireNonNullElse(payload, ""));

        var inserted =
                jdbcTemplate.update(
                        """
                        INSERT INTO sys_task_inbox
                            (task_id, task_type, payload_digest, completed_at)
                        VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                        ON CONFLICT (task_id) DO NOTHING
                        """,
                        normalizedTaskId,
                        normalizedTaskType,
                        payloadDigest);
        if (inserted == 0) {
            var existing =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT task_type, payload_digest, result
                            FROM sys_task_inbox
                            WHERE task_id = ?
                            """,
                            (resultSet, rowNum) ->
                                    new InboxRecord(
                                            resultSet.getString("task_type"),
                                            resultSet.getString("payload_digest"),
                                            resultSet.getString("result")),
                            normalizedTaskId);
            if (existing == null
                    || !normalizedTaskType.equals(existing.taskType())
                    || !payloadDigest.equals(existing.payloadDigest())) {
                throw new IllegalStateException("任务 ID 已被不同类型或载荷占用: " + normalizedTaskId);
            }
            return existing.result();
        }

        var result = handler.get();
        jdbcTemplate.update(
                "UPDATE sys_task_inbox SET result = ? WHERE task_id = ?", result, normalizedTaskId);
        return result;
    }

    private String digest(String payload) {
        try {
            var bytes =
                    MessageDigest.getInstance("SHA-256")
                            .digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }

    private String requireText(String value, String field) {
        var normalized = Objects.requireNonNull(value, field + " 不能为空").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return normalized;
    }

    private record InboxRecord(String taskType, String payloadDigest, String result) {}
}
