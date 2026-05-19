package com.xuejiai.aaf.framework.task;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 任务执行监控。记录任务执行状态，支持超时检测。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskMonitor {

    private final JdbcTemplate jdbcTemplate;

    /** 超时阈值（分钟） */
    private static final long TIMEOUT_MINUTES = 30;

    /** 记录任务开始，返回 executionId */
    public Long recordStart(String taskName, String taskType) {
        var sql = """
                INSERT INTO sys_task_execution (task_name, task_type, status, start_time, create_time)
                VALUES (?, ?, 'running', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """;
        return jdbcTemplate.queryForObject(sql, Long.class, taskName, taskType);
    }

    /** 记录任务成功 */
    public void recordSuccess(Long executionId) {
        if (executionId == null) return;
        var sql = """
                UPDATE sys_task_execution
                SET status = 'success', end_time = CURRENT_TIMESTAMP,
                    duration_ms = EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - start_time)) * 1000
                WHERE id = ?
                """;
        jdbcTemplate.update(sql, executionId);
    }

    /** 记录任务失败 */
    public void recordFailure(Long executionId, String error) {
        if (executionId == null) return;
        var sql = """
                UPDATE sys_task_execution
                SET status = 'failed', end_time = CURRENT_TIMESTAMP,
                    duration_ms = EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - start_time)) * 1000,
                    error_message = ?
                WHERE id = ?
                """;
        jdbcTemplate.update(sql, error, executionId);
    }

    /** 增加重试计数 */
    public void incrementRetry(Long executionId) {
        if (executionId == null) return;
        jdbcTemplate.update("UPDATE sys_task_execution SET retry_count = retry_count + 1 WHERE id = ?", executionId);
    }

    /** 定时扫描超时任务（每 5 分钟） */
    @Scheduled(fixedDelay = 300_000)
    public void detectTimeout() {
        var sql = """
                UPDATE sys_task_execution
                SET status = 'timeout', end_time = CURRENT_TIMESTAMP
                WHERE status = 'running' AND start_time < ?
                """;
        var threshold = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);
        var count = jdbcTemplate.update(sql, threshold);
        if (count > 0) {
            log.warn("检测到 {} 个超时任务", count);
        }
    }
}
