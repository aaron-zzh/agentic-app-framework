package com.xuejiai.aaf.framework.engine.workflow.node;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 抄送节点——将审批流程抄送给指定用户，不阻塞流程。
 *
 * <p>流程变量：ccUsers（逗号分隔的用户 ID 列表）、taskName（当前节点名称）、entityType、entityId
 */
@Slf4j
@Component("carbonCopyNode")
@RequiredArgsConstructor
public class CarbonCopyNode implements JavaDelegate {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void execute(DelegateExecution execution) {
        var ccUsers = (String) execution.getVariable("ccUsers");
        if (ccUsers == null || ccUsers.isBlank()) {
            log.warn("抄送节点未配置抄送人: processInstance={}", execution.getProcessInstanceId());
            return;
        }

        var taskName = execution.getCurrentActivityName();
        var entityType = (String) execution.getVariable("entityType");
        var entityId = (String) execution.getVariable("entityId");

        // 发布抄送事件，由业务层监听处理
        eventPublisher.publishEvent(
                new CarbonCopyEvent(
                        execution.getProcessInstanceId(), taskName, ccUsers, entityType, entityId));

        log.info(
                "抄送节点执行完成: processInstance={}, ccUsers={}",
                execution.getProcessInstanceId(),
                ccUsers);
    }

    /** 抄送事件，由业务层监听并持久化。 */
    public record CarbonCopyEvent(
            String processInstanceId,
            String taskName,
            String ccUsers,
            String entityType,
            String entityId) {}
}
