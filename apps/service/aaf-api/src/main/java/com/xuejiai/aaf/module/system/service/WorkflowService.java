/*
 * 需要在 aaf-dependencies/pom.xml 中注册版本，并在 aaf-framework/pom.xml 中引入：
 * <dependency>
 *     <groupId>org.flowable</groupId>
 *     <artifactId>flowable-spring-boot-starter</artifactId>
 * </dependency>
 */
package com.xuejiai.aaf.module.system.service;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.system.vo.WorkflowStatusVO;

import lombok.RequiredArgsConstructor;

/** 工作流服务，封装 Flowable RuntimeService/TaskService。 */
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private static final String PROCESS_KEY = "generic-approval";

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;

    /** 启动审批流程。 */
    @Transactional
    public String startProcess(String entityType, Long entityId, String initiator, String assignee) {
        var variables = Map.<String, Object>of(
                "entityType", entityType,
                "entityId", entityId,
                "initiator", initiator,
                "assignee", assignee);
        ProcessInstance instance =
                runtimeService.startProcessInstanceByKey(PROCESS_KEY, entityType + ":" + entityId, variables);
        return instance.getId();
    }

    /** 通过审批。 */
    @Transactional
    public void completeTask(String taskId, String comment) {
        Task task = getTask(taskId);
        if (comment != null) {
            taskService.addComment(taskId, task.getProcessInstanceId(), comment);
        }
        taskService.setVariable(taskId, "approved", true);
        taskService.complete(taskId);
    }

    /** 驳回审批。 */
    @Transactional
    public void rejectTask(String taskId, String comment) {
        Task task = getTask(taskId);
        if (comment != null) {
            taskService.addComment(taskId, task.getProcessInstanceId(), comment);
        }
        taskService.setVariable(taskId, "approved", false);
        taskService.complete(taskId);
    }

    /** 查询流程状态。 */
    @Transactional(readOnly = true)
    public WorkflowStatusVO getStatus(String processInstanceId) {
        var historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (historicInstance == null) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "流程实例不存在");
        }

        // 当前待办任务
        Task currentTask = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        var history = getHistory(processInstanceId);

        return new WorkflowStatusVO(
                processInstanceId,
                (String) historicInstance.getProcessVariables().get("entityType"),
                ((Number) historicInstance.getProcessVariables().get("entityId")).longValue(),
                (String) historicInstance.getProcessVariables().get("initiator"),
                historicInstance.getEndTime() != null,
                currentTask != null ? currentTask.getId() : null,
                currentTask != null ? currentTask.getAssignee() : null,
                history);
    }

    /** 查询审批历史。 */
    @Transactional(readOnly = true)
    public List<WorkflowStatusVO.HistoryItem> getHistory(String processInstanceId) {
        return historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .finished()
                .orderByHistoricTaskInstanceEndTime()
                .asc()
                .includeProcessVariables()
                .list()
                .stream()
                .map(t -> new WorkflowStatusVO.HistoryItem(
                        t.getName(),
                        t.getAssignee(),
                        Boolean.TRUE.equals(t.getProcessVariables().get("approved")) ? "通过" : "驳回",
                        null,
                        t.getEndTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()))
                .toList();
    }

    private Task getTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "任务不存在");
        }
        return task;
    }
}
