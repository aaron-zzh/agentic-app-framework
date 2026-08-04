package com.xuejiai.aaf.framework.engine.bpmn.flowable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.bpmn.api.BpmnEngine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Flowable 实现的工作流引擎。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowableBpmnEngine implements BpmnEngine {

    private static final String ORG_ID_VARIABLE = "_aafOrgId";
    private static final String WORKSPACE_ID_VARIABLE = "_aafWorkspaceId";
    private static final long ORGANIZATION_SCOPE = 0L;

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final RepositoryService repositoryService;

    // ==================== 原有方法 ====================

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
        if (variables == null || variables.isEmpty()) {
            taskService.complete(taskId);
        } else {
            taskService.setVariablesLocal(taskId, variables);
            taskService.complete(taskId, variables);
        }
    }

    @Override
    public TaskInfo getTask(String taskId) {
        var task = taskService.createTaskQuery().taskId(taskId).singleResult();
        return task == null ? null : toTaskInfo(task);
    }

    @Override
    public boolean isTaskAt(String taskId, String processKey, String taskDefinitionKey) {
        return taskService
                        .createTaskQuery()
                        .taskId(taskId)
                        .processDefinitionKey(processKey)
                        .taskDefinitionKey(taskDefinitionKey)
                        .count()
                > 0;
    }

    @Override
    public boolean canOperateTask(String taskId, String userId) {
        var assigned =
                taskService.createTaskQuery().taskId(taskId).taskAssignee(userId).count() > 0;
        if (assigned) {
            return true;
        }
        return taskService.createTaskQuery().taskId(taskId).taskCandidateUser(userId).count() > 0;
    }

    @Override
    public boolean hasTaskParticipant(String processInstanceId, String userId) {
        if (historyService
                        .createHistoricTaskInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .taskAssignee(userId)
                        .count()
                > 0) {
            return true;
        }
        if (historyService.getHistoricIdentityLinksForProcessInstance(processInstanceId).stream()
                .anyMatch(
                        link ->
                                userId.equals(link.getUserId())
                                        && IdentityLinkType.CANDIDATE.equals(link.getType()))) {
            return true;
        }
        var tasks = taskService.createTaskQuery().processInstanceId(processInstanceId).list();
        return tasks.stream()
                .anyMatch(
                        task ->
                                userId.equals(task.getAssignee())
                                        || taskService
                                                        .createTaskQuery()
                                                        .taskId(task.getId())
                                                        .taskCandidateUser(userId)
                                                        .count()
                                                > 0);
    }

    @Override
    public boolean hasOperableTask(String processKey, String userId, Long orgId, Long workspaceId) {
        var scopeWorkspaceId = workspaceId != null ? workspaceId : ORGANIZATION_SCOPE;
        return taskService
                                .createTaskQuery()
                                .processDefinitionKey(processKey)
                                .processVariableValueEquals(ORG_ID_VARIABLE, orgId)
                                .processVariableValueEquals(WORKSPACE_ID_VARIABLE, scopeWorkspaceId)
                                .taskAssignee(userId)
                                .count()
                        > 0
                || taskService
                                .createTaskQuery()
                                .processDefinitionKey(processKey)
                                .processVariableValueEquals(ORG_ID_VARIABLE, orgId)
                                .processVariableValueEquals(WORKSPACE_ID_VARIABLE, scopeWorkspaceId)
                                .taskCandidateUser(userId)
                                .count()
                        > 0;
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
                .includeTaskLocalVariables()
                .list()
                .stream()
                .map(
                        task ->
                                new HistoryRecord(
                                        task.getName(),
                                        task.getAssignee(),
                                        task.getTaskLocalVariables() != null
                                                ? task.getTaskLocalVariables()
                                                : Map.of(),
                                        latestTaskComment(task.getId()),
                                        task.getEndTime().getTime()))
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

    @Override
    public List<TaskInfo> listPendingTasks(String assignee) {
        return taskService
                .createTaskQuery()
                .taskAssignee(assignee)
                .orderByTaskCreateTime()
                .desc()
                .list()
                .stream()
                .map(this::toTaskInfo)
                .toList();
    }

    @Override
    public List<DefinitionInfo> listDefinitions() {
        return repositoryService
                .createProcessDefinitionQuery()
                .latestVersion()
                .orderByProcessDefinitionName()
                .asc()
                .list()
                .stream()
                .map(this::toDefinitionInfo)
                .toList();
    }

    @Override
    public String deploy(String name, String bpmnXml) {
        var deployment =
                repositoryService
                        .createDeployment()
                        .name(name)
                        .addInputStream(
                                name + ".bpmn20.xml",
                                new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)))
                        .deploy();
        return deployment.getId();
    }

    @Override
    public String findInstanceByBusinessKey(String businessKey) {
        var instance =
                runtimeService
                        .createProcessInstanceQuery()
                        .processInstanceBusinessKey(businessKey)
                        .singleResult();
        if (instance != null) return instance.getId();
        // 查历史（已完成的流程）
        var historic =
                historyService
                        .createHistoricProcessInstanceQuery()
                        .processInstanceBusinessKey(businessKey)
                        .orderByProcessInstanceStartTime()
                        .desc()
                        .list();
        return historic.isEmpty() ? null : historic.getFirst().getId();
    }

    @Override
    public boolean isProcessRunning(String processInstanceId) {
        return runtimeService
                        .createProcessInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .count()
                > 0;
    }

    @Override
    public InstanceInfo getInstance(String processInstanceId) {
        var running =
                runtimeService
                        .createProcessInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .singleResult();
        if (running != null) {
            return new InstanceInfo(
                    running.getId(),
                    running.getProcessDefinitionKey(),
                    running.getBusinessKey(),
                    running.isSuspended() ? "suspended" : "running",
                    running.getStartTime().getTime(),
                    null);
        }
        var historic =
                historyService
                        .createHistoricProcessInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .singleResult();
        return historic == null ? null : toInstanceInfo(historic);
    }

    // ==================== #5802 流程定义管理 ====================

    @Override
    public List<DefinitionInfo> queryDefinitions(
            String key, String name, int pageNo, int pageSize) {
        var query = buildDefinitionQuery(key, name);
        return query
                .orderByProcessDefinitionName()
                .asc()
                .listPage((pageNo - 1) * pageSize, pageSize)
                .stream()
                .map(this::toDefinitionInfo)
                .toList();
    }

    @Override
    public long countDefinitions(String key, String name) {
        return buildDefinitionQuery(key, name).count();
    }

    @Override
    public List<DefinitionInfo> listDefinitionVersions(String processKey) {
        return repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .orderByProcessDefinitionVersion()
                .desc()
                .list()
                .stream()
                .map(this::toDefinitionInfo)
                .toList();
    }

    @Override
    public void suspendDefinition(String processDefinitionId) {
        repositoryService.suspendProcessDefinitionById(processDefinitionId);
    }

    @Override
    public void activateDefinition(String processDefinitionId) {
        repositoryService.activateProcessDefinitionById(processDefinitionId);
    }

    @Override
    public void deleteDeployment(String deploymentId, boolean cascade) {
        repositoryService.deleteDeployment(deploymentId, cascade);
    }

    @Override
    public String exportDefinitionXml(String processDefinitionId) {
        var definition = repositoryService.getProcessDefinition(processDefinitionId);
        try (InputStream is =
                repositoryService.getResourceAsStream(
                        definition.getDeploymentId(), definition.getResourceName())) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("导出流程定义 XML 失败: " + processDefinitionId, e);
        }
    }

    // ==================== #5803 流程实例管理 ====================

    @Override
    public List<InstanceInfo> listRunningInstances(
            String processKey, Long orgId, Long workspaceId, int pageNo, int pageSize) {
        var query = buildRunningInstanceQuery(processKey, orgId, workspaceId);
        return query
                .orderByProcessInstanceId()
                .desc()
                .listPage((pageNo - 1) * pageSize, pageSize)
                .stream()
                .map(
                        pi ->
                                new InstanceInfo(
                                        pi.getId(),
                                        pi.getProcessDefinitionKey(),
                                        pi.getBusinessKey(),
                                        pi.isSuspended() ? "suspended" : "running",
                                        pi.getStartTime().getTime(),
                                        null))
                .toList();
    }

    @Override
    public long countRunningInstances(String processKey, Long orgId, Long workspaceId) {
        return buildRunningInstanceQuery(processKey, orgId, workspaceId).count();
    }

    @Override
    public List<InstanceInfo> listHistoricInstances(
            String processKey,
            boolean finished,
            Long orgId,
            Long workspaceId,
            int pageNo,
            int pageSize) {
        var query = buildHistoricInstanceQuery(processKey, finished, orgId, workspaceId);
        return query
                .orderByProcessInstanceStartTime()
                .desc()
                .listPage((pageNo - 1) * pageSize, pageSize)
                .stream()
                .map(this::toInstanceInfo)
                .toList();
    }

    @Override
    public long countHistoricInstances(
            String processKey, boolean finished, Long orgId, Long workspaceId) {
        return buildHistoricInstanceQuery(processKey, finished, orgId, workspaceId).count();
    }

    @Override
    public void suspendInstance(String processInstanceId) {
        runtimeService.suspendProcessInstanceById(processInstanceId);
    }

    @Override
    public void activateInstance(String processInstanceId) {
        runtimeService.activateProcessInstanceById(processInstanceId);
    }

    @Override
    public void terminateInstance(String processInstanceId, String reason) {
        runtimeService.deleteProcessInstance(processInstanceId, reason);
    }

    @Override
    public void deleteInstance(String processInstanceId, String reason) {
        // 先删运行时，再删历史
        try {
            runtimeService.deleteProcessInstance(processInstanceId, reason);
        } catch (Exception ignored) {
            // 可能已结束
        }
        historyService.deleteHistoricProcessInstance(processInstanceId);
    }

    @Override
    public void setProcessVariables(String processInstanceId, Map<String, Object> variables) {
        runtimeService.setVariables(processInstanceId, variables);
    }

    // ==================== #5804 任务分配与流转 ====================

    @Override
    public List<TaskInfo> listCandidateTasks(String candidateUser) {
        return taskService
                .createTaskQuery()
                .taskCandidateUser(candidateUser)
                .orderByTaskCreateTime()
                .desc()
                .list()
                .stream()
                .map(this::toTaskInfo)
                .toList();
    }

    @Override
    public List<TaskInfo> listCandidateGroupTasks(String candidateGroup) {
        return taskService
                .createTaskQuery()
                .taskCandidateGroup(candidateGroup)
                .orderByTaskCreateTime()
                .desc()
                .list()
                .stream()
                .map(this::toTaskInfo)
                .toList();
    }

    @Override
    public List<InstanceInfo> listInstances(
            String processKey, Map<String, Object> variableEquals, int pageNo, int pageSize) {
        return buildInstanceQuery(processKey, variableEquals)
                .orderByProcessInstanceStartTime()
                .desc()
                .listPage((pageNo - 1) * pageSize, pageSize)
                .stream()
                .map(this::toInstanceInfo)
                .toList();
    }

    @Override
    public long countInstances(String processKey, Map<String, Object> variableEquals) {
        return buildInstanceQuery(processKey, variableEquals).count();
    }

    @Override
    public void claimTask(String taskId, String userId) {
        taskService.claim(taskId, userId);
    }

    @Override
    public void delegateTask(String taskId, String delegateUserId) {
        taskService.delegateTask(taskId, delegateUserId);
    }

    @Override
    public void moveTaskToPreviousCompleted(String taskId) {
        var task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) throw new IllegalArgumentException("任务不存在: " + taskId);

        var historicTasks =
                historyService
                        .createHistoricTaskInstanceQuery()
                        .processInstanceId(task.getProcessInstanceId())
                        .finished()
                        .orderByHistoricTaskInstanceEndTime()
                        .desc()
                        .list();
        if (historicTasks.isEmpty()) {
            throw new IllegalStateException("没有上一个已完成的用户任务");
        }

        var previousTask = historicTasks.getFirst();
        runtimeService
                .createChangeActivityStateBuilder()
                .processInstanceId(task.getProcessInstanceId())
                .moveActivityIdTo(task.getTaskDefinitionKey(), previousTask.getTaskDefinitionKey())
                .changeState();
        log.info(
                "任务节点移动：{} -> {}",
                task.getTaskDefinitionKey(),
                previousTask.getTaskDefinitionKey());
    }

    @Override
    public void addTaskComment(String taskId, String type, String message) {
        var task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) throw new IllegalArgumentException("任务不存在: " + taskId);
        taskService.addComment(taskId, task.getProcessInstanceId(), type, message);
    }

    @Override
    public void addMultiInstanceExecution(String taskId, Map<String, Object> variables) {
        var task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) throw new IllegalArgumentException("任务不存在: " + taskId);
        runtimeService.addMultiInstanceExecution(
                task.getTaskDefinitionKey(), task.getProcessInstanceId(), variables);
    }

    // ==================== #5805 信号与消息事件 ====================

    @Override
    public void sendSignal(
            String signalName, String processInstanceId, Map<String, Object> variables) {
        var execution =
                runtimeService
                        .createExecutionQuery()
                        .processInstanceId(processInstanceId)
                        .signalEventSubscriptionName(signalName)
                        .singleResult();
        if (execution == null) {
            throw new IllegalStateException(
                    "未找到等待信号 '%s' 的执行，流程实例: %s".formatted(signalName, processInstanceId));
        }
        if (variables == null || variables.isEmpty()) {
            runtimeService.signalEventReceived(signalName, execution.getId());
        } else {
            runtimeService.signalEventReceived(signalName, execution.getId(), variables);
        }
    }

    @Override
    public void sendMessage(
            String messageName, String processInstanceId, Map<String, Object> variables) {
        // 查找等待该消息的执行
        var execution =
                runtimeService
                        .createExecutionQuery()
                        .processInstanceId(processInstanceId)
                        .messageEventSubscriptionName(messageName)
                        .singleResult();
        if (execution == null) {
            throw new IllegalStateException(
                    "未找到等待消息 '%s' 的执行，流程实例: %s".formatted(messageName, processInstanceId));
        }
        runtimeService.messageEventReceived(messageName, execution.getId(), variables);
    }

    // ==================== 内部辅助方法 ====================

    private String latestTaskComment(String taskId) {
        var comments = taskService.getTaskComments(taskId);
        return comments.isEmpty() ? null : comments.getLast().getFullMessage();
    }

    private TaskInfo toTaskInfo(Task t) {
        return new TaskInfo(t.getId(), t.getProcessInstanceId(), t.getAssignee(), t.getName());
    }

    private DefinitionInfo toDefinitionInfo(ProcessDefinition d) {
        return new DefinitionInfo(
                d.getKey(), d.getName(), d.getVersion(), d.getId(), d.isSuspended());
    }

    private InstanceInfo toInstanceInfo(HistoricProcessInstance h) {
        String status;
        if (h.getEndTime() != null && h.getDeleteReason() != null) {
            status = "terminated";
        } else if (h.getEndTime() != null) {
            status = "completed";
        } else {
            status = "running";
        }
        return new InstanceInfo(
                h.getId(),
                h.getProcessDefinitionKey(),
                h.getBusinessKey(),
                status,
                h.getStartTime().getTime(),
                h.getEndTime() != null ? h.getEndTime().getTime() : null);
    }

    private ProcessDefinitionQuery buildDefinitionQuery(String key, String name) {
        var query = repositoryService.createProcessDefinitionQuery();
        if (key != null && !key.isBlank()) {
            query.processDefinitionKey(key);
        }
        if (name != null && !name.isBlank()) {
            query.processDefinitionNameLike("%" + name + "%");
        }
        return query;
    }

    private ProcessInstanceQuery buildRunningInstanceQuery(
            String processKey, Long orgId, Long workspaceId) {
        var query =
                runtimeService
                        .createProcessInstanceQuery()
                        .variableValueEquals(ORG_ID_VARIABLE, orgId)
                        .variableValueEquals(
                                WORKSPACE_ID_VARIABLE,
                                workspaceId != null ? workspaceId : ORGANIZATION_SCOPE);
        if (processKey != null && !processKey.isBlank()) {
            query.processDefinitionKey(processKey);
        }
        return query;
    }

    private HistoricProcessInstanceQuery buildInstanceQuery(
            String processKey, Map<String, Object> variableEquals) {
        var query = historyService.createHistoricProcessInstanceQuery();
        if (processKey != null && !processKey.isBlank()) {
            query.processDefinitionKey(processKey);
        }
        for (var entry : variableEquals.entrySet()) {
            query.variableValueEquals(entry.getKey(), entry.getValue());
        }
        return query;
    }

    private HistoricProcessInstanceQuery buildHistoricInstanceQuery(
            String processKey, boolean finished, Long orgId, Long workspaceId) {
        var query =
                historyService
                        .createHistoricProcessInstanceQuery()
                        .variableValueEquals(ORG_ID_VARIABLE, orgId)
                        .variableValueEquals(
                                WORKSPACE_ID_VARIABLE,
                                workspaceId != null ? workspaceId : ORGANIZATION_SCOPE);
        if (processKey != null && !processKey.isBlank()) {
            query.processDefinitionKey(processKey);
        }
        if (finished) {
            query.finished();
        }
        return query;
    }
}
