package com.xuejiai.aaf.framework.engine.workflow.trigger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.workflow.WorkflowEngine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 定时触发器——Cron 表达式驱动工作流自动执行。
 *
 * <p>支持动态注册/注销定时任务，每次触发时启动指定工作流。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CronTriggerService {

    private final TaskScheduler taskScheduler;
    private final WorkflowEngine workflowEngine;
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * 注册定时触发器。
     *
     * @param triggerId 触发器唯一标识
     * @param cronExpression Cron 表达式
     * @param processKey 要启动的工作流 key
     * @param variables 工作流启动变量
     */
    public void register(String triggerId, String cronExpression, String processKey, Map<String, Object> variables) {
        cancel(triggerId);
        var future = taskScheduler.schedule(
                () -> {
                    try {
                        var instanceId = workflowEngine.startProcess(processKey, triggerId, variables);
                        log.info("定时触发器执行: triggerId={} processKey={} instanceId={}", triggerId, processKey, instanceId);
                    } catch (Exception e) {
                        log.error("定时触发器执行失败: triggerId={}", triggerId, e);
                    }
                },
                new CronTrigger(cronExpression));
        scheduledTasks.put(triggerId, future);
        log.info("注册定时触发器: triggerId={} cron={} processKey={}", triggerId, cronExpression, processKey);
    }

    /** 注销定时触发器 */
    public void cancel(String triggerId) {
        var future = scheduledTasks.remove(triggerId);
        if (future != null) {
            future.cancel(false);
            log.info("注销定时触发器: triggerId={}", triggerId);
        }
    }

    /** 获取所有活跃触发器 ID */
    public java.util.Set<String> listActive() {
        return scheduledTasks.keySet();
    }
}
