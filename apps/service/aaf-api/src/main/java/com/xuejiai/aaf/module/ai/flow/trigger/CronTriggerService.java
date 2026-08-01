package com.xuejiai.aaf.module.ai.flow.trigger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.ai.flow.service.AiFlowTriggerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 定时触发器——Cron 表达式驱动 AI Flow 自动执行。
 *
 * <p>支持动态注册/注销定时任务，每次触发时重新验证并启动指定 AI Flow。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CronTriggerService {

    private final TaskScheduler taskScheduler;
    private final AiFlowTriggerService triggerService;
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * 注册定时触发器。
     *
     * @param triggerId 触发器唯一标识
     * @param cronExpression Cron 表达式
     * @param flowId 要启动的 AI Flow 业务 ID
     * @param variables 流程启动变量
     */
    public void register(
            String triggerId, String cronExpression, Long flowId, Map<String, Object> variables) {
        var normalizedTriggerId = requireNonBlank(triggerId, "触发器 ID 不能为空");
        var normalizedCronExpression = requireNonBlank(cronExpression, "Cron 表达式不能为空");
        var cronTrigger = new CronTrigger(normalizedCronExpression);
        var identity = triggerService.currentIdentity();
        var capturedVariables = immutableCopy(variables);
        triggerService.validateTriggerable(flowId, identity, capturedVariables);

        var newFuture =
                taskScheduler.schedule(
                        () -> execute(normalizedTriggerId, flowId, capturedVariables, identity),
                        cronTrigger);
        if (newFuture == null) {
            throw new BusinessException(GlobalErrorCode.SERVICE_UNAVAILABLE, "定时触发器注册失败");
        }

        var previous = scheduledTasks.put(normalizedTriggerId, newFuture);
        if (previous != null) {
            previous.cancel(false);
        }
        log.info(
                "注册定时触发器: triggerId={} cron={} flowId={}",
                normalizedTriggerId,
                normalizedCronExpression,
                flowId);
    }

    private void execute(
            String triggerId,
            Long flowId,
            Map<String, Object> variables,
            AiFlowTriggerService.TrustedIdentity identity) {
        try {
            var result = triggerService.triggerCron(flowId, triggerId, variables, identity);
            log.info(
                    "定时触发器执行: triggerId={} flowId={} instanceId={} businessKey={}",
                    triggerId,
                    result.flowId(),
                    result.processInstanceId(),
                    result.businessKey());
        } catch (Exception e) {
            log.error("定时触发器执行失败: triggerId={} flowId={}", triggerId, flowId, e);
        }
    }

    private String requireNonBlank(String value, String message) {
        if (value == null) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, message);
        }
        var normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, message);
        }
        return normalized;
    }

    private Map<String, Object> immutableCopy(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new HashMap<>(variables));
    }

    /** 注销定时触发器。 */
    public void cancel(String triggerId) {
        var normalizedTriggerId = requireNonBlank(triggerId, "触发器 ID 不能为空");
        var future = scheduledTasks.remove(normalizedTriggerId);
        if (future != null) {
            future.cancel(false);
            log.info("注销定时触发器: triggerId={}", normalizedTriggerId);
        }
    }

    /** 获取所有活跃触发器 ID。 */
    public Set<String> listActive() {
        return Set.copyOf(scheduledTasks.keySet());
    }
}
