package com.xuejiai.aaf.framework.task;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 定时任务执行器。
 *
 * <p>启动时优先从 {@link TaskPersistencePort}（DB）加载持久化任务， 再合并内存注册的任务（DB 优先）。执行前获取 Redis 分布式锁防止集群重复执行，
 * 执行后回写 last_run 到 DB，支持重启 misfire 补偿。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTaskExecutor {

    private static final String LOCK_PREFIX = "task_lock:";
    private static final long DEFAULT_LOCK_TTL_SECONDS = 300;

    private final TaskScheduler taskScheduler;
    private final TaskRegistry taskRegistry;
    private final StringRedisTemplate redisTemplate;
    private final BeanFactory beanFactory;
    private final TaskMonitor taskMonitor;

    /** 可选——api 层提供实现，framework 层测试时可不注入 */
    private final ObjectProvider<TaskPersistencePort> persistencePort;

    private final Map<String, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        // 1. 从 DB 加载持久化任务并注册到内存（DB 优先覆盖）
        persistencePort.ifAvailable(
                port -> {
                    var dbTasks = port.loadActiveTasks();
                    dbTasks.forEach(taskRegistry::register);
                    log.info("从 DB 加载 {} 个持久化任务", dbTasks.size());
                });

        // 2. 调度所有启用任务（含 misfire 检查）
        taskRegistry.listAll().stream()
                .filter(TaskDefinition::enabled)
                .forEach(
                        def -> {
                            checkMisfire(def);
                            schedule(def);
                        });
    }

    /** 调度单个任务（按 triggerType 选择调度方式） */
    public void schedule(TaskDefinition def) {
        cancel(def.name());
        var future =
                switch (def.triggerType()) {
                    case CRON ->
                            taskScheduler.schedule(
                                    () -> executeWithLock(def),
                                    new CronTrigger(def.cronExpression()));
                    case FIXED_DELAY ->
                            taskScheduler.scheduleWithFixedDelay(
                                    () -> executeWithLock(def),
                                    java.time.Duration.ofMillis(def.intervalMs()));
                    case FIXED_RATE ->
                            taskScheduler.scheduleAtFixedRate(
                                    () -> executeWithLock(def),
                                    java.time.Duration.ofMillis(def.intervalMs()));
                };
        scheduledFutures.put(def.name(), future);
        log.info(
                "调度任务: {} [{}={}]",
                def.name(),
                def.triggerType(),
                def.triggerType() == TriggerType.CRON
                        ? def.cronExpression()
                        : def.intervalMs() + "ms");
    }

    /** 取消调度 */
    public void cancel(String name) {
        var future = scheduledFutures.remove(name);
        if (future != null) future.cancel(false);
    }

    /** 手动触发一次 */
    public void triggerOnce(String name) {
        var def = taskRegistry.get(name);
        if (def != null)
            taskScheduler.schedule(() -> executeWithLock(def), java.time.Instant.now());
    }

    /** 启动时检查 misfire，从 DB last_run 判断是否需要补跑 */
    private void checkMisfire(TaskDefinition def) {
        if (def.misfirePolicy() == MisfirePolicy.IGNORE) return;
        // 优先读 DB last_run
        var lastRun = getLastRunFromDb(def.name());
        if (lastRun == null) return;

        try {
            var lastRunInstant = lastRun.atZone(java.time.ZoneId.systemDefault()).toInstant();
            var trigger = new CronTrigger(def.cronExpression());
            var ctx = new org.springframework.scheduling.support.SimpleTriggerContext();
            ctx.update(lastRunInstant, lastRunInstant, lastRunInstant);
            var nextAfterLast = trigger.nextExecution(ctx);
            if (nextAfterLast != null && nextAfterLast.isBefore(java.time.Instant.now())) {
                log.warn("任务 [{}] 检测到错过执行（预期 {}），补跑一次", def.name(), nextAfterLast);
                taskScheduler.schedule(() -> executeWithLock(def), java.time.Instant.now());
            }
        } catch (Exception e) {
            log.warn("任务 [{}] misfire 检查失败", def.name(), e);
        }
    }

    private LocalDateTime getLastRunFromDb(String taskName) {
        var port = persistencePort.getIfAvailable();
        return port != null ? port.getLastRun(taskName) : null;
    }

    private void executeWithLock(TaskDefinition def) {
        var lockKey = LOCK_PREFIX + def.name();
        var acquired =
                Boolean.TRUE.equals(
                        redisTemplate
                                .opsForValue()
                                .setIfAbsent(
                                        lockKey, "1", DEFAULT_LOCK_TTL_SECONDS, TimeUnit.SECONDS));
        if (!acquired) {
            log.debug("任务 [{}] 未获取到锁，跳过执行", def.name());
            return;
        }
        var executionId = taskMonitor.recordStart(def.name(), "scheduled");
        var now = LocalDateTime.now();
        try {
            beanFactory.getBean(def.taskClass()).run();
            taskMonitor.recordSuccess(executionId);
            // 回写 last_run 到 DB
            persistencePort.ifAvailable(port -> port.updateLastRun(def.name(), now));
        } catch (Exception e) {
            log.error("定时任务 [{}] 执行失败", def.name(), e);
            taskMonitor.recordFailure(executionId, e.getMessage());
            persistencePort.ifAvailable(port -> port.recordFailure(def.name(), e.getMessage()));
        } finally {
            redisTemplate.delete(lockKey);
        }
    }
}
