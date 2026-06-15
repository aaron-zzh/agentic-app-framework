package com.xuejiai.aaf.module.system.task.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.system.notify.service.NotificationService;
import com.xuejiai.aaf.module.system.task.action.ScheduledActionExecutor;
import com.xuejiai.aaf.module.system.task.domain.ScheduledTask;
import com.xuejiai.aaf.module.system.task.repository.ScheduledTaskRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 计划任务业务逻辑。管理 DB 驱动的定时任务（cron / fixedDelay / fixedRate）。
 *
 * <h3>适用场景</h3>
 *
 * <ul>
 *   <li>按时间规则自动触发的后台任务（cron 表达式或固定间隔）
 *   <li>需要持久化执行记录、失败重试、错过补偿（misfire）
 *   <li>支持动作类型扩展：NOTIFY（推送通知）/ WORKFLOW（触发 Flowable 流程）/ WEBHOOK
 *   <li>典型：定时生成报表、定时清理过期数据、定时触发 AI 工作流
 * </ul>
 *
 * <h3>不适用场景</h3>
 *
 * <ul>
 *   <li>用户主动触发、需要进度反馈 → 用 AsyncTaskService（内存异步）
 *   <li>高并发消息驱动、需要优先级队列 → 用 Redis Stream 任务队列
 *   <li>需要人工审批、多步骤状态流转 → 用 Flowable 工作流
 * </ul>
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTaskService {

    private final ScheduledTaskRepository scheduledTaskRepository;
    private final NotificationService notificationService;
    private final List<ScheduledActionExecutor> actionExecutors;
    private final HolidayCalendarService holidayCalendarService;

    /** actionType → executor 映射，启动时构建 */
    private Map<String, ScheduledActionExecutor> executorMap;

    @jakarta.annotation.PostConstruct
    void init() {
        executorMap =
                actionExecutors.stream()
                        .collect(
                                Collectors.toMap(
                                        ScheduledActionExecutor::actionType, Function.identity()));
    }

    /** 连续失败阈值 */
    @Value("${aaf.scheduled-task.max-fail-count:3}")
    private int maxFailCount;

    /** 查询所有计划任务 */
    public List<ScheduledTask> list() {
        return scheduledTaskRepository.findAll();
    }

    /** 暂停任务 */
    @Transactional
    public void pause(Long id) {
        var task = getById(id);
        task.setStatus("paused");
        scheduledTaskRepository.save(task);
    }

    /** 恢复任务 */
    @Transactional
    public void resume(Long id) {
        var task = getById(id);
        task.setStatus("active");
        task.setFailCount(0);
        scheduledTaskRepository.save(task);
    }

    /** 手动触发执行 */
    @Transactional
    public void runNow(Long id) {
        var task = getById(id);
        try {
            executeAction(task);
            task.setLastRun(LocalDateTime.now());
            task.setFailCount(0);
            task.setStatus("active");
            scheduledTaskRepository.save(task);
            log.info("手动触发计划任务: {} ({})", task.getName(), task.getType());
        } catch (Exception e) {
            recordFailure(task);
            throw e;
        }
    }

    /** 创建用户自定义计划任务 */
    @Transactional
    public ScheduledTask createUserTask(
            String name,
            String cron,
            String actionType,
            String actionConfig,
            String misfirePolicy) {
        if (!executorMap.containsKey(actionType)) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST,
                    "不支持的动作类型: " + actionType + "，支持: " + executorMap.keySet());
        }
        var task = new ScheduledTask();
        task.setName(name);
        task.setType("user_defined");
        task.setCron(cron);
        task.setActionType(actionType);
        task.setActionConfig(actionConfig);
        task.setMisfirePolicy(misfirePolicy != null ? misfirePolicy : "IGNORE");
        return scheduledTaskRepository.save(task);
    }

    /** 执行任务动作（含日历排除判断） */
    public void executeAction(ScheduledTask task) {
        // 日历排除检查
        if (holidayCalendarService.isExcluded(java.time.LocalDate.now(), task.getCalendarCode())) {
            log.info("任务 [{}] 命中日历排除 ({})，跳过执行", task.getName(), task.getCalendarCode());
            return;
        }
        if (task.getActionType() == null) return;
        var executor = executorMap.get(task.getActionType());
        if (executor == null) {
            log.warn("未找到动作执行器: {}", task.getActionType());
            return;
        }
        executor.execute(task);
    }

    /** 记录任务失败，连续失败超阈值自动暂停并通知 */
    @Transactional
    public void recordFailure(ScheduledTask task) {
        task.setFailCount(task.getFailCount() + 1);
        if (task.getFailCount() >= maxFailCount) {
            task.setStatus("failed");
            log.warn("计划任务 [{}] 连续失败 {} 次，已自动暂停", task.getName(), task.getFailCount());
            // 通知管理员（userId=1 为系统管理员）
            notificationService.sendSystemNotification(
                    1L,
                    "计划任务告警",
                    "任务 [%s] 连续失败 %d 次，已自动暂停".formatted(task.getName(), task.getFailCount()));
        }
        scheduledTaskRepository.save(task);
    }

    /** 根据 ID 查询任务 */
    public ScheduledTask getById(Long id) {
        return scheduledTaskRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "计划任务不存在"));
    }
}
