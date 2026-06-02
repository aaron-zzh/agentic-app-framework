package com.xuejiai.aaf.module.ai.chat.service;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.module.ai.chat.domain.ChatTask;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 任务调度器——定时扫描到期任务并触发持久执行。
 *
 * <p>一致性保障：
 *
 * <ul>
 *   <li>通过 DurableTaskExecutor 的 CAS 抢占防止重复执行
 *   <li>孤儿回收：running 超时的执行实例重置为 pending
 *   <li>检查点恢复：崩溃后从最近检查点继续
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatTaskScheduler {

    private final ChatTaskService taskService;
    private final DurableTaskExecutor durableExecutor;

    /** 定时扫描到期任务（每 30 秒） */
    @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
    public void pollDueTasks() {
        var dueTasks = taskService.findDueTasks();
        for (var task : dueTasks) {
            if (taskService.tryStart(task.getId())) {
                Thread.startVirtualThread(() -> executeWithDurability(task));
            }
        }
    }

    /** 定时回收孤儿（每 2 分钟） */
    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void recoverOrphans() {
        int recovered = durableExecutor.recoverOrphans();
        if (recovered > 0) {
            log.warn("[TaskScheduler] 回收孤儿执行实例: {} 个", recovered);
        }
        // 同时回收 ChatTask 级别的孤儿
        var cutoff = LocalDateTime.now().minusMinutes(10);
        int taskRecovered = taskService.recoverOrphans(cutoff);
        if (taskRecovered > 0) {
            log.warn("[TaskScheduler] 回收孤儿任务: {} 个", taskRecovered);
        }
    }

    /** 持久执行任务 */
    public void executeWithDurability(ChatTask task) {
        log.info("[TaskScheduler] 开始持久执行: taskId={}, title={}", task.getId(), task.getTitle());

        // 创建执行实例
        var execution = durableExecutor.createExecution(task);

        // CAS 抢占执行实例
        if (!durableExecutor.tryStart(execution.getId())) {
            log.debug("[TaskScheduler] 执行实例抢占失败: executionId={}", execution.getId());
            return;
        }

        try {
            durableExecutor.execute(task, execution);
            taskService.complete(task.getId(), "执行完成");
            log.info("[TaskScheduler] 任务完成: taskId={}", task.getId());

            // 自动执行下一个
            executeNext(task.getSessionId());
        } catch (Exception e) {
            log.error("[TaskScheduler] 任务执行失败: taskId={}", task.getId(), e);
            taskService.fail(task.getId(), e.getMessage());
        }
    }

    /** 手动触发执行任务 */
    public void executeTask(ChatTask task) {
        if (taskService.tryStart(task.getId())) {
            executeWithDurability(task);
        }
    }

    /** 自动取下一个待处理任务并执行 */
    public void executeNext(Long sessionId) {
        taskService
                .nextPending(sessionId)
                .ifPresent(next -> Thread.startVirtualThread(() -> executeTask(next)));
    }
}
