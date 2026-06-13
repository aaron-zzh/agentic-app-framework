package com.xuejiai.aaf.module.system.task.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.task.MisfirePolicy;
import com.xuejiai.aaf.framework.task.TaskDefinition;
import com.xuejiai.aaf.framework.task.TaskPersistencePort;
import com.xuejiai.aaf.framework.task.TriggerType;
import com.xuejiai.aaf.module.system.task.domain.ScheduledTask;
import com.xuejiai.aaf.module.system.task.repository.ScheduledTaskRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link TaskPersistencePort} 的数据库实现。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>启动时从 {@code sys_scheduled_task} 加载 active 任务，转换为 {@link TaskDefinition} 供框架调度
 *   <li>执行后回写 {@code last_run}，支持重启 misfire 补偿
 *   <li>记录连续失败，超阈值自动将任务状态标记为 {@code failed}
 * </ul>
 *
 * <p>内置系统任务通过 {@code BUILTIN_BEAN_MAP} 按 type 映射到 Spring Bean 名称； 用户自定义任务（type=user_defined）使用
 * {@link UserDefinedTaskRunnable}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbTaskPersistencePort implements TaskPersistencePort {

    private final ScheduledTaskRepository repository;
    private final ApplicationContext applicationContext;

    /** 内置系统任务 type → Spring Bean 名映射。 新增系统任务时在此注册，无需修改框架层代码。 */
    private static final Map<String, String> BUILTIN_BEAN_MAP =
            Map.of(
                    "weekly_credit", "weeklyCreditScheduler",
                    "subscription_credit", "subscriptionCreditScheduler",
                    "credit_expire", "creditExpireScheduler",
                    "pay_order_expire", "payOrderExpireTask",
                    "pay_order_sync", "payOrderSyncTask",
                    "image_sync", "imageSyncJob",
                    "memory_maintenance", "memoryMaintenanceTask");

    /** 连续失败自动暂停阈值 */
    private static final int MAX_FAIL_COUNT = 3;

    @Override
    public List<TaskDefinition> loadActiveTasks() {
        return repository.findByStatus("active").stream()
                .map(this::toDefinition)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    @Override
    public LocalDateTime getLastRun(String taskName) {
        return repository.findByName(taskName).map(ScheduledTask::getLastRun).orElse(null);
    }

    @Override
    @Transactional
    public void updateLastRun(String taskName, LocalDateTime lastRun) {
        repository
                .findByName(taskName)
                .ifPresent(
                        t -> {
                            t.setLastRun(lastRun);
                            t.setFailCount(0); // 成功执行后重置失败计数
                            repository.save(t);
                        });
    }

    @Override
    @Transactional
    public void recordFailure(String taskName, String errorMsg) {
        repository
                .findByName(taskName)
                .ifPresent(
                        t -> {
                            t.setFailCount(t.getFailCount() + 1);
                            if (t.getFailCount() >= MAX_FAIL_COUNT) {
                                t.setStatus("failed");
                                log.warn("任务 [{}] 连续失败 {} 次，已自动暂停", taskName, t.getFailCount());
                            }
                            repository.save(t);
                        });
    }

    /**
     * 将 DB 记录转为框架层 {@link TaskDefinition}。 转换失败（如 Bean 不存在）时返回 {@link Optional#empty()}，该任务跳过调度。
     */
    private Optional<TaskDefinition> toDefinition(ScheduledTask task) {
        try {
            var taskClass = resolveTaskClass(task);
            if (taskClass == null) return Optional.empty();
            var misfire =
                    "RUN_ONCE".equals(task.getMisfirePolicy())
                            ? MisfirePolicy.RUN_ONCE
                            : MisfirePolicy.IGNORE;
            var triggerType = TriggerType.CRON;
            if ("FIXED_DELAY".equals(task.getTriggerType())) triggerType = TriggerType.FIXED_DELAY;
            else if ("FIXED_RATE".equals(task.getTriggerType()))
                triggerType = TriggerType.FIXED_RATE;
            return Optional.of(
                    new TaskDefinition(
                            task.getName(),
                            task.getCron(),
                            task.getIntervalMs(),
                            triggerType,
                            taskClass,
                            "active".equals(task.getStatus()),
                            task.getRemark(),
                            misfire));
        } catch (Exception e) {
            log.warn("任务 [{}] 定义解析失败，跳过调度: {}", task.getName(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 解析任务对应的 Runnable Class。 内置任务按 BUILTIN_BEAN_MAP 查找 Bean；用户自定义任务使用 {@link
     * UserDefinedTaskRunnable}。
     */
    @SuppressWarnings("unchecked")
    private Class<? extends Runnable> resolveTaskClass(ScheduledTask task) {
        var beanName = BUILTIN_BEAN_MAP.get(task.getType());
        if (beanName != null) {
            var bean = applicationContext.getBean(beanName);
            if (bean instanceof Runnable r) return (Class<? extends Runnable>) r.getClass();
            log.warn("Bean [{}] 未实现 Runnable 接口", beanName);
            return null;
        }
        if ("user_defined".equals(task.getType())) {
            return UserDefinedTaskRunnable.class;
        }
        log.warn("未知任务类型 [{}]，无法解析 taskClass", task.getType());
        return null;
    }
}
