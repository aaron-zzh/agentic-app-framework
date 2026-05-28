package com.xuejiai.aaf.module.system.workflow.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.engine.workflow.WorkflowEngine;
import com.xuejiai.aaf.module.system.workflow.vo.WorkflowStatusVO;

import lombok.RequiredArgsConstructor;

/**
 * 工作流服务，委托给 WorkflowEngine 引擎层接口。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private static final String PROCESS_KEY = "generic-approval";

    private final WorkflowEngine workflowEngine;

    /** 启动审批流程。 */
    @Transactional
    public String startProcess(
            String entityType, Long entityId, String initiator, String assignee) {
        var variables =
                Map.<String, Object>of(
                        "entityType", entityType,
                        "entityId", entityId,
                        "initiator", initiator,
                        "assignee", assignee);
        return workflowEngine.startProcess(PROCESS_KEY, entityType + ":" + entityId, variables);
    }

    /** 通过审批。 */
    @Transactional
    public void completeTask(String taskId, String comment) {
        workflowEngine.completeTask(taskId, Map.of("approved", true), comment);
    }

    /** 驳回审批。 */
    @Transactional
    public void rejectTask(String taskId, String comment) {
        workflowEngine.completeTask(taskId, Map.of("approved", false), comment);
    }

    /** 查询流程状态。 */
    @Transactional(readOnly = true)
    public WorkflowStatusVO getStatus(String processInstanceId) {
        var currentTask = workflowEngine.getCurrentTask(processInstanceId);
        var variables = workflowEngine.getProcessVariables(processInstanceId);
        var history = getHistory(processInstanceId);

        return new WorkflowStatusVO(
                processInstanceId,
                (String) variables.get("entityType"),
                variables.get("entityId") instanceof Number n ? n.longValue() : null,
                (String) variables.get("initiator"),
                currentTask == null,
                currentTask != null ? currentTask.taskId() : null,
                currentTask != null ? currentTask.assignee() : null,
                history);
    }

    /** 查询审批历史。 */
    @Transactional(readOnly = true)
    public List<WorkflowStatusVO.HistoryItem> getHistory(String processInstanceId) {
        return workflowEngine.getHistory(processInstanceId).stream()
                .map(
                        r ->
                                new WorkflowStatusVO.HistoryItem(
                                        r.taskName(),
                                        r.assignee(),
                                        r.outcome(),
                                        r.comment(),
                                        LocalDateTime.ofInstant(
                                                Instant.ofEpochMilli(r.completedAtMs()),
                                                ZoneId.systemDefault())))
                .toList();
    }
}
