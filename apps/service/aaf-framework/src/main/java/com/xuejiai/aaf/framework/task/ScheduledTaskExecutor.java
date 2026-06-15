package com.xuejiai.aaf.framework.task;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.meta.runtime.ExecutionMeta;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 定时任务核心调度器。
 *
 * <h3>整体架构</h3>
 *
 * <pre>
 * 启动流程：
 *   ApplicationReadyEvent
 *     → DbTaskPersistencePort.loadActiveTasks()  从 sys_scheduled_task 加载活跃任务
 *     → TaskRegistry.register()                  注册到内存任务表
 *     → checkMisfire()                           检查重启期间是否有任务被跳过
 *     → schedule()                               提交到 ThreadPoolTaskScheduler
 *
 * 执行流程：
 *   ThreadPoolTaskScheduler 触发（cron / fixedDelay / fixedRate）
 *     → executeWithLock()
 *         → Redis setIfAbsent 抢分布式锁（防集群重复执行）
 *         → TaskRuntime.submit()               委托元引擎运行时执行
 *             → TaskExecutionListener.onStart()  记录执行开始（DB 写入在 task 层）
 *             → AafTask.execute()               执行业务逻辑
 *             → TaskExecutionListener.onSuccess/Failure()
 *         → DbTaskPersistencePort.updateLastRun() 回写 last_run 到 DB
 *         → Redis delete 释放锁
 * </pre>
 *
 * <h3>任务类型与 Bean 解析</h3>
 *
 * <p>业务 Bean 必须实现 {@link Runnable}。框架通过 {@link TaskDefinition#taskClass()} 从 Spring 容器取 Bean：
 *
 * <ul>
 *   <li>内置任务：{@code DbTaskPersistencePort.BUILTIN_BEAN_MAP} 维护 type → beanName 映射
 *   <li>用户自定义：统一走 {@code UserDefinedTaskRunnable}，由 actionConfig JSON 决定执行动作
 * </ul>
 *
 * <h3>分布式锁</h3>
 *
 * <p>锁 key = {@code task_lock:{taskName}}，TTL = {@value DEFAULT_LOCK_TTL_SECONDS}s。
 * 集群多节点同时触发时，只有抢到锁的节点执行，其余节点静默跳过。 TTL 需大于任务最长执行时间，避免执行中锁过期导致重复执行。
 *
 * <h3>Misfire 补偿</h3>
 *
 * <p>应用重启后，读取 DB {@code last_run} 与 cron 计算出"本该执行的下次时间"对比当前时间：
 *
 * <ul>
 *   <li>{@link MisfirePolicy#IGNORE}（默认）：跳过，等下次 cron 触发
 *   <li>{@link MisfirePolicy#RUN_ONCE}：立即补跑一次
 * </ul>
 *
 * <h3>动态管理</h3>
 *
 * <p>运行时可通过 {@link com.xuejiai.aaf.module.system.task.controller.TaskManagementController} 调用
 * {@link #schedule}、{@link #cancel}、{@link #triggerOnce} 实现热更新，无需重启。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTaskExecutor {

    /** Redis 分布式锁 key 前缀 */
    private static final String LOCK_PREFIX = "task_lock:";

    /** 分布式锁 TTL（秒）。 必须大于任务最长执行时间，否则锁过期后其他节点会重复执行。 */
    private static final long DEFAULT_LOCK_TTL_SECONDS = 300;

    /** Spring 线程池调度器，由 {@link TaskAutoConfiguration} 创建，默认池大小由配置决定 */
    private final TaskScheduler taskScheduler;

    /** 内存任务注册表，持有所有已注册的 TaskDefinition */
    private final TaskRegistry taskRegistry;

    /** Redis 客户端，用于分布式锁 */
    private final StringRedisTemplate redisTemplate;

    /** Spring Bean 工厂，用于按 taskClass 获取业务 Runnable Bean */
    private final BeanFactory beanFactory;

    /** 元引擎统一任务运行时，负责执行 + 监控回调 */
    private final com.xuejiai.aaf.framework.engine.meta.runtime.TaskRuntime taskRuntime;

    /** DB 持久化端口（可选）。 aaf-api 通过 {@code DbTaskPersistencePort} 提供实现。 */
    private final ObjectProvider<TaskPersistencePort> persistencePort;

    /** 正在调度中的 Future 表，用于 cancel/暂停 */
    private final Map<String, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();

    /** 应用启动完成后初始化所有任务， 确保所有 Bean（包括业务 Runnable）已完成初始化后再调度。 */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        // 1. 从 DB 加载持久化任务并注册到内存（DB 记录覆盖代码内注册的同名任务）
        persistencePort.ifAvailable(
                port -> {
                    var dbTasks = port.loadActiveTasks();
                    dbTasks.forEach(taskRegistry::register);
                    log.info("从 DB 加载 {} 个持久化任务", dbTasks.size());
                });

        // 2. 将所有任务注册到元引擎运行时（Runnable → AafTask 适配器）
        taskRegistry
                .listAll()
                .forEach(
                        def -> {
                            var bean = beanFactory.getBean(def.taskClass());
                            taskRuntime.register(
                                    new ScheduledTaskAdapter(
                                            def.name(), bean, def.timeoutSeconds()));
                        });

        // 3. 调度所有启用任务（逐个检查 misfire 后提交到线程池）
        taskRegistry.listAll().stream()
                .filter(TaskDefinition::enabled)
                .forEach(
                        def -> {
                            checkMisfire(def);
                            schedule(def);
                        });
    }

    /**
     * 调度单个任务到线程池。 若同名任务已在调度，先 cancel 再重新调度（支持热更新 cron 表达式）。
     *
     * @param def 任务定义
     */
    public void schedule(TaskDefinition def) {
        // 先取消旧的调度（支持热更新）
        cancel(def.name());

        var future =
                switch (def.triggerType()) {
                    // cron 表达式调度，由 Spring CronTrigger 解析
                    case CRON ->
                            taskScheduler.schedule(
                                    () -> executeWithLock(def),
                                    new CronTrigger(def.cronExpression()));
                    // 上次执行结束后等待 intervalMs 再执行
                    case FIXED_DELAY ->
                            taskScheduler.scheduleWithFixedDelay(
                                    () -> executeWithLock(def),
                                    Duration.ofMillis(def.intervalMs()));
                    // 固定频率，不管上次是否结束
                    case FIXED_RATE ->
                            taskScheduler.scheduleAtFixedRate(
                                    () -> executeWithLock(def),
                                    Duration.ofMillis(def.intervalMs()));
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

    /** 取消任务调度（不中断正在执行的任务）。 用于暂停任务或热更新前的清理。 */
    public void cancel(String name) {
        var future = scheduledFutures.remove(name);
        if (future != null) future.cancel(false); // false = 不中断已在执行的线程
    }

    /**
     * 立即手动触发一次任务（不影响原有 cron 调度）。 由 {@link
     * com.xuejiai.aaf.module.system.task.controller.TaskManagementController} 调用。
     */
    public void triggerOnce(String name) {
        var def = taskRegistry.get(name);
        if (def != null) taskScheduler.schedule(() -> executeWithLock(def), Instant.now());
    }

    /**
     * 启动时检查任务是否在宕机/重启期间错过了执行。 仅 {@link MisfirePolicy#RUN_ONCE} 策略会触发补跑， {@link
     * MisfirePolicy#IGNORE}（默认）直接跳过。
     */
    private void checkMisfire(TaskDefinition def) {
        if (def.misfirePolicy() == MisfirePolicy.IGNORE) return;
        var lastRun = getLastRunFromDb(def.name());
        if (lastRun == null) return; // 从未执行过，无需补跑

        try {
            var lastRunInstant = lastRun.atZone(ZoneId.systemDefault()).toInstant();
            var trigger = new CronTrigger(def.cronExpression());
            var ctx = new SimpleTriggerContext();
            ctx.update(lastRunInstant, lastRunInstant, lastRunInstant);
            // 计算上次执行之后"本该触发"的下一次时间
            var nextAfterLast = trigger.nextExecution(ctx);
            if (nextAfterLast != null && nextAfterLast.isBefore(Instant.now())) {
                log.warn("任务 [{}] 检测到错过执行（预期 {}），补跑一次", def.name(), nextAfterLast);
                taskScheduler.schedule(() -> executeWithLock(def), Instant.now());
            }
        } catch (Exception e) {
            log.warn("任务 [{}] misfire 检查失败", def.name(), e);
        }
    }

    private LocalDateTime getLastRunFromDb(String taskName) {
        var port = persistencePort.getIfAvailable();
        return port != null ? port.getLastRun(taskName) : null;
    }

    /**
     * 带分布式锁的任务执行，是所有任务的实际执行入口。
     *
     * <p>执行步骤：
     *
     * <ol>
     *   <li>Redis setIfAbsent 抢锁，失败则跳过（集群其他节点已在执行）
     *   <li>委托 TaskRuntime.submit() 执行（含监控回调）
     *   <li>回写 last_run 到 DB
     *   <li>finally 释放 Redis 锁
     * </ol>
     */
    private void executeWithLock(TaskDefinition def) {
        var lockKey = LOCK_PREFIX + def.name();

        // 抢分布式锁，setIfAbsent = SET key value NX PX ttl
        var acquired =
                Boolean.TRUE.equals(
                        redisTemplate
                                .opsForValue()
                                .setIfAbsent(
                                        lockKey, "1", DEFAULT_LOCK_TTL_SECONDS, TimeUnit.SECONDS));
        if (!acquired) {
            log.debug("任务 [{}] 未获取到锁，跳过执行（集群其他节点正在执行）", def.name());
            return;
        }

        var now = LocalDateTime.now();
        try {
            // 通过元引擎运行时执行（含执行监控回调）
            var result =
                    taskRuntime.submit(
                            def.name(), null, ExecutionMeta.scheduled(def.triggerType().name()));
            // 回写执行时间到 DB，供 misfire 补偿使用
            persistencePort.ifAvailable(port -> port.updateLastRun(def.name(), now));
            if (!result.success()) {
                persistencePort.ifAvailable(port -> port.recordFailure(def.name(), result.error()));
            }
        } catch (Exception e) {
            log.error("定时任务 [{}] 执行失败", def.name(), e);
            // 记录失败次数，连续失败超阈值自动暂停任务
            persistencePort.ifAvailable(port -> port.recordFailure(def.name(), e.getMessage()));
        } finally {
            // 无论成功失败都释放锁
            redisTemplate.delete(lockKey);
        }
    }
}
