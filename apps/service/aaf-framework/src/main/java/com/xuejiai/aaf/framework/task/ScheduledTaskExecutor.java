package com.xuejiai.aaf.framework.task;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.BeanFactory;
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
 * <p>启动时从 TaskRegistry 加载已注册任务，执行前获取 Redis 分布式锁防止集群重复执行。
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

    private final Map<String, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        taskRegistry.listAll().stream()
                .filter(TaskDefinition::enabled)
                .forEach(this::schedule);
    }

    /** 调度单个任务 */
    public void schedule(TaskDefinition def) {
        cancel(def.name());
        var future = taskScheduler.schedule(() -> executeWithLock(def), new CronTrigger(def.cronExpression()));
        scheduledFutures.put(def.name(), future);
        log.info("调度定时任务: {} [{}]", def.name(), def.cronExpression());
    }

    /** 取消调度 */
    public void cancel(String name) {
        var future = scheduledFutures.remove(name);
        if (future != null) {
            future.cancel(false);
        }
    }

    /** 手动触发一次 */
    public void triggerOnce(String name) {
        var def = taskRegistry.get(name);
        if (def != null) {
            taskScheduler.schedule(() -> executeWithLock(def), java.time.Instant.now());
        }
    }

    private void executeWithLock(TaskDefinition def) {
        var lockKey = LOCK_PREFIX + def.name();
        var acquired = Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(lockKey, "1", DEFAULT_LOCK_TTL_SECONDS, TimeUnit.SECONDS));
        if (!acquired) {
            log.debug("任务 {} 未获取到锁，跳过执行", def.name());
            return;
        }
        var executionId = taskMonitor.recordStart(def.name(), "scheduled");
        try {
            var task = beanFactory.getBean(def.taskClass());
            task.run();
            taskMonitor.recordSuccess(executionId);
        } catch (Exception e) {
            log.error("定时任务 {} 执行失败", def.name(), e);
            taskMonitor.recordFailure(executionId, e.getMessage());
        } finally {
            redisTemplate.delete(lockKey);
        }
    }
}
