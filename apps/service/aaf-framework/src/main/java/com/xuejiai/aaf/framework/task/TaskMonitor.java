package com.xuejiai.aaf.framework.task;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 任务执行监控。记录任务执行状态，支持超时检测和历史自动清理。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskMonitor {

    private final TaskExecutionRepository repository;

    /** 超时阈值（分钟） */
    private static final long TIMEOUT_MINUTES = 30;

    @org.springframework.beans.factory.annotation.Value("${aaf.task.execution-retention-days:90}")
    private int retentionDays;

    /** 记录任务开始，返回 executionId */
    public Long recordStart(String taskName, String taskType) {
        return recordStart(taskName, taskType, null, null, null, null);
    }

    /** 记录任务开始（含业务 ID 和上下文快照），返回 executionId */
    public Long recordStart(String taskName, String taskType, String bizId, String context) {
        return recordStart(taskName, taskType, bizId, context, null, null);
    }

    /** 记录任务开始（完整参数） */
    public Long recordStart(
            String taskName,
            String taskType,
            String bizId,
            String context,
            Short priority,
            String triggerType) {
        var execution = new TaskExecution();
        execution.setTaskName(taskName);
        execution.setTaskType(taskType);
        execution.setStatus("running");
        execution.setStartTime(LocalDateTime.now());
        execution.setCreateTime(LocalDateTime.now());
        execution.setBizId(bizId);
        execution.setContext(context);
        execution.setPriority(priority);
        execution.setTriggerType(triggerType);
        return repository.save(execution).getId();
    }

    /** 记录任务成功 */
    public void recordSuccess(Long executionId) {
        if (executionId == null) return;
        repository
                .findById(executionId)
                .ifPresent(
                        e -> {
                            e.setStatus("success");
                            e.setEndTime(LocalDateTime.now());
                            e.setDurationMs(
                                    java.time.Duration.between(e.getStartTime(), e.getEndTime())
                                            .toMillis());
                            repository.save(e);
                        });
    }

    /** 记录任务失败 */
    public void recordFailure(Long executionId, String error) {
        if (executionId == null) return;
        repository
                .findById(executionId)
                .ifPresent(
                        e -> {
                            e.setStatus("failed");
                            e.setEndTime(LocalDateTime.now());
                            e.setDurationMs(
                                    java.time.Duration.between(e.getStartTime(), e.getEndTime())
                                            .toMillis());
                            e.setErrorMessage(error);
                            repository.save(e);
                        });
    }

    /** 增加重试计数 */
    public void incrementRetry(Long executionId) {
        if (executionId == null) return;
        repository
                .findById(executionId)
                .ifPresent(
                        e -> {
                            e.setRetryCount(e.getRetryCount() + 1);
                            repository.save(e);
                        });
    }

    /** 定时扫描超时任务（每 5 分钟） */
    @Scheduled(fixedDelay = 300_000)
    public void detectTimeout() {
        var threshold = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);
        var running =
                repository.findByStatus(
                        "running", org.springframework.data.domain.Pageable.unpaged());
        var timedOut = running.stream().filter(e -> e.getStartTime().isBefore(threshold)).toList();
        if (!timedOut.isEmpty()) {
            timedOut.forEach(
                    e -> {
                        e.setStatus("timeout");
                        e.setEndTime(LocalDateTime.now());
                    });
            repository.saveAll(timedOut);
            log.warn("检测到 {} 个超时任务", timedOut.size());
        }
    }

    /** 清理过期执行历史（每天凌晨 3:00） */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupHistory() {
        if (retentionDays < 0) return;
        var threshold = LocalDateTime.now().minusDays(retentionDays);
        var expired =
                repository.findAll().stream()
                        .filter(e -> e.getCreateTime().isBefore(threshold))
                        .filter(e -> !e.getStatus().equals("running"))
                        .toList();
        if (!expired.isEmpty()) {
            repository.deleteAll(expired);
            log.info("清理过期任务执行历史 {} 条（保留 {} 天）", expired.size(), retentionDays);
        }
    }
}
