package com.xuejiai.aaf.framework.engine.workflow;

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
import org.flowable.task.api.Task;
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
    public List<InstanceInfo> listRunningInstances(String processKey, int pageNo, int pageSize) {
        var query = buildRunningInstanceQuery(processKey);
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
    public long countRunningInstances(String processKey) {
        return buildRunningInstanceQuery(processKey).count();
    }

    @Override
    public List<InstanceInfo> listHistoricInstances(
            String processKey, boolean finished, int pageNo, int pageSize) {
        var query = buildHistoricInstanceQuery(processKey, finished);
        return query
                .orderByProcessInstanceStartTime()
                .desc()
                .listPage((pageNo - 1) * pageSize, pageSize)
                .stream()
                .map(this::toInstanceInfo)
                .toList();
    }

    @Override
    public long countHistoricInstances(String processKey, boolean finished) {
        return buildHistoricInstanceQuery(processKey, finished).count();
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
    public List<InstanceInfo> listMyInitiatedInstances(String initiator, int pageNo, int pageSize) {
        return historyService
                .createHistoricProcessInstanceQuery()
                .startedBy(initiator)
                .orderByProcessInstanceStartTime()
                .desc()
                .listPage((pageNo - 1) * pageSize, pageSize)
                .stream()
                .map(this::toInstanceInfo)
                .toList();
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
    public void returnTask(String taskId, String reason) {
        var task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) throw new IllegalArgumentException("任务不存在: " + taskId);

        // 查询上一个已完成的用户任务
        var historicTasks =
                historyService
                        .createHistoricTaskInstanceQuery()
                        .processInstanceId(task.getProcessInstanceId())
                        .finished()
                        .orderByHistoricTaskInstanceEndTime()
                        .desc()
                        .list();

        if (historicTasks.isEmpty()) {
            throw new IllegalStateException("无法退回：没有上一步任务");
        }

        var previousTask = historicTasks.get(0);
        // 使用 Flowable 的回退能力
        runtimeService
                .createChangeActivityStateBuilder()
                .processInstanceId(task.getProcessInstanceId())
                .moveActivityIdTo(task.getTaskDefinitionKey(), previousTask.getTaskDefinitionKey())
                .changeState();

        log.info(
                "任务退回：{} -> {}，原因：{}",
                task.getTaskDefinitionKey(),
                previousTask.getTaskDefinitionKey(),
                reason);
    }

    @Override
    public void urgeTask(String taskId, String urgerId) {
        var task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) throw new IllegalArgumentException("任务不存在: " + taskId);

        // 通过任务评论记录催办信息
        taskService.addComment(
                taskId,
                task.getProcessInstanceId(),
                "URGE",
                "催办人: " + urgerId + "，时间: " + java.time.LocalDateTime.now());
        log.info("催办任务：taskId={}，催办人={}", taskId, urgerId);
    }

    // ==================== #5805 信号与消息事件 ====================

    @Override
    public void sendSignal(String signalName) {
        runtimeService.signalEventReceived(signalName);
    }

    @Override
    public void sendSignal(String signalName, Map<String, Object> variables) {
        runtimeService.signalEventReceived(signalName, variables);
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

    // ==================== 审批操作 ====================

    @Override
    public void addSign(String taskId, String assignee) {
        var task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) throw new IllegalArgumentException("任务不存在: " + taskId);
        runtimeService.addMultiInstanceExecution(
                task.getTaskDefinitionKey(),
                task.getProcessInstanceId(),
                Map.of("assignee", assignee));
        log.info("加签完成：taskId={}, assignee={}", taskId, assignee);
    }

    @Override
    public void transferSign(String taskId, String targetAssignee, String reason) {
        var task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) throw new IllegalArgumentException("任务不存在: " + taskId);
        var originalAssignee = task.getAssignee();
        taskService.setAssignee(taskId, targetAssignee);
        if (reason != null) {
            taskService.addComment(
                    taskId,
                    task.getProcessInstanceId(),
                    "转签：%s → %s，原因：%s".formatted(originalAssignee, targetAssignee, reason));
        }
        log.info("转签完成：taskId={}, {} → {}", taskId, originalAssignee, targetAssignee);
    }

    @Override
    public void withdraw(String processInstanceId, String initiator) {
        var variables = runtimeService.getVariables(processInstanceId);
        var processInitiator = (String) variables.get("initiator");
        if (!initiator.equals(processInitiator)) {
            throw new IllegalArgumentException("只有发起人可以撤回");
        }
        boolean hasOtherCompleted =
                historyService
                        .createHistoricActivityInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .activityType("userTask")
                        .finished()
                        .list()
                        .stream()
                        .anyMatch(
                                t -> t.getAssignee() != null && !t.getAssignee().equals(initiator));
        if (hasOtherCompleted) {
            throw new IllegalStateException("后续节点已处理，无法撤回");
        }
        runtimeService.deleteProcessInstance(processInstanceId, "发起人撤回");
        log.info("流程撤回：processInstanceId={}, initiator={}", processInstanceId, initiator);
    }

    // ==================== 内部辅助方法 ====================

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

    private ProcessInstanceQuery buildRunningInstanceQuery(String processKey) {
        var query = runtimeService.createProcessInstanceQuery();
        if (processKey != null && !processKey.isBlank()) {
            query.processDefinitionKey(processKey);
        }
        return query;
    }

    private HistoricProcessInstanceQuery buildHistoricInstanceQuery(
            String processKey, boolean finished) {
        var query = historyService.createHistoricProcessInstanceQuery();
        if (processKey != null && !processKey.isBlank()) {
            query.processDefinitionKey(processKey);
        }
        if (finished) {
            query.finished();
        }
        return query;
    }
}
