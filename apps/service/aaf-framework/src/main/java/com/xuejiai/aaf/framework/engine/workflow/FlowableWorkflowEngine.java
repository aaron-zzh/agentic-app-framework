package com.xuejiai.aaf.framework.engine.workflow;

import java.util.List;
import java.util.Map;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Flowable 实现的工作流引擎。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowableWorkflowEngine implements WorkflowEngine {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;

    @Override
    public String startProcess(
            String processKey, String businessKey, Map<String, Object> variables) {
        var instance = runtimeService.startProcessInstanceByKey(processKey, businessKey, variables);
        return instance.getId();
    }

    @Override
    public void completeTask(String taskId, Map<String, Object> variables, String comment) {
        var task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) throw new IllegalArgumentException("任务不存在: " + taskId);
        if (comment != null) {
            taskService.addComment(taskId, task.getProcessInstanceId(), comment);
        }
        if (variables != null) {
            taskService.setVariablesLocal(taskId, variables);
        }
        taskService.complete(taskId);
    }

    @Override
    public TaskInfo getCurrentTask(String processInstanceId) {
        var task =
                taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();
        if (task == null) return null;
        return new TaskInfo(task.getId(), processInstanceId, task.getAssignee(), task.getName());
    }

    @Override
    public List<HistoryRecord> getHistory(String processInstanceId) {
        return historyService
                .createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .finished()
                .orderByHistoricTaskInstanceEndTime()
                .asc()
                .includeProcessVariables()
                .list()
                .stream()
                .map(
                        t ->
                                new HistoryRecord(
                                        t.getName(),
                                        t.getAssignee(),
                                        Boolean.TRUE.equals(t.getProcessVariables().get("approved"))
                                                ? "通过"
                                                : "驳回",
                                        null,
                                        t.getEndTime().getTime()))
                .toList();
    }

    @Override
    public void reassignTask(String taskId, String newAssignee) {
        taskService.setAssignee(taskId, newAssignee);
    }

    @Override
    public Map<String, Object> getProcessVariables(String processInstanceId) {
        var historic =
                historyService
                        .createHistoricProcessInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .includeProcessVariables()
                        .singleResult();
        if (historic == null) return Map.of();
        return historic.getProcessVariables() != null ? historic.getProcessVariables() : Map.of();
    }
}
