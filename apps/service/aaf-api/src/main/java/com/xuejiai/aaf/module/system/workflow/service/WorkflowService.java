package com.xuejiai.aaf.module.system.workflow.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.framework.engine.workflow.WorkflowEngine;
import com.xuejiai.aaf.module.system.workflow.vo.ProcessDefinitionVO;
import com.xuejiai.aaf.module.system.workflow.vo.ProcessInstanceVO;
import com.xuejiai.aaf.module.system.workflow.vo.WorkflowDeployDTO;
import com.xuejiai.aaf.module.system.workflow.vo.WorkflowPublishDTO;
import com.xuejiai.aaf.module.system.workflow.vo.WorkflowStatusVO;
import com.xuejiai.aaf.module.system.workflow.vo.WorkflowTaskVO;
import com.xuejiai.aaf.module.system.workflow.vo.WorkflowVersionVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作流服务，委托给 WorkflowEngine 引擎层接口。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private static final String PROCESS_KEY = "generic-approval";

    private final WorkflowEngine workflowEngine;

    // ==================== 原有方法 ====================

    /**
     * 启动审批流程。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @param initiator 发起人
     * @param assignee 审批人
     * @return 流程实例 ID
     */
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

    /**
     * 按实体类型和 ID 查询关联流程状态。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @return 流程状态（无关联流程时返回 status=none 的空对象）
     */
    @Transactional(readOnly = true)
    public WorkflowStatusVO getStatusByEntity(String entityType, String entityId) {
        String businessKey = entityType + ":" + entityId;
        var instances = workflowEngine.listRunningInstances(0, 1);
        // 通过 businessKey 查找（遍历运行中实例匹配）
        var processInstanceId = workflowEngine.findInstanceByBusinessKey(businessKey);
        if (processInstanceId == null) {
            return new WorkflowStatusVO(null, entityType, Long.valueOf(entityId), null, false, null, null, List.of());
        }
        return getStatus(processInstanceId);
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

    /** 查询指定审批人的待办任务列表。 */
    @Transactional(readOnly = true)
    public List<WorkflowTaskVO> listPendingTasks(String assignee) {
        return workflowEngine.listPendingTasks(assignee).stream()
                .map(this::toTaskVO)
                .toList();
    }

    /** 查询所有流程定义（最新版本）。 */
    @Transactional(readOnly = true)
    public List<ProcessDefinitionVO> listDefinitions() {
        return workflowEngine.listDefinitions().stream()
                .map(this::toDefinitionVO)
                .toList();
    }

    /** 部署流程定义。 */
    @Transactional
    public String deployDefinition(WorkflowDeployDTO dto) {
        return workflowEngine.deploy(dto.name(), dto.bpmnXml());
    }

    // ==================== #5802 流程定义管理 ====================

    /** 分页查询流程定义。 */
    @Transactional(readOnly = true)
    public PageResult<ProcessDefinitionVO> queryDefinitions(
            String key, String name, int pageNo, int pageSize) {
        var list = workflowEngine.queryDefinitions(key, name, pageNo, pageSize).stream()
                .map(this::toDefinitionVO)
                .toList();
        long total = workflowEngine.countDefinitions(key, name);
        return new PageResult<>(list, total);
    }

    /** 查询指定 key 的所有版本。 */
    @Transactional(readOnly = true)
    public List<ProcessDefinitionVO> listDefinitionVersions(String processKey) {
        return workflowEngine.listDefinitionVersions(processKey).stream()
                .map(this::toDefinitionVO)
                .toList();
    }

    /** 挂起流程定义。 */
    @Transactional
    public void suspendDefinition(String processDefinitionId) {
        workflowEngine.suspendDefinition(processDefinitionId);
    }

    /** 激活流程定义。 */
    @Transactional
    public void activateDefinition(String processDefinitionId) {
        workflowEngine.activateDefinition(processDefinitionId);
    }

    /** 删除流程定义。 */
    @Transactional
    public void deleteDeployment(String deploymentId, boolean cascade) {
        workflowEngine.deleteDeployment(deploymentId, cascade);
    }

    /** 导出流程定义 XML。 */
    @Transactional(readOnly = true)
    public String exportDefinitionXml(String processDefinitionId) {
        return workflowEngine.exportDefinitionXml(processDefinitionId);
    }

    // ==================== #5803 流程实例管理 ====================

    /** 分页查询运行中的流程实例。 */
    @Transactional(readOnly = true)
    public PageResult<ProcessInstanceVO> listRunningInstances(
            String processKey, int pageNo, int pageSize) {
        var list = workflowEngine.listRunningInstances(processKey, pageNo, pageSize).stream()
                .map(this::toInstanceVO)
                .toList();
        long total = workflowEngine.countRunningInstances(processKey);
        return new PageResult<>(list, total);
    }

    /** 分页查询历史流程实例。 */
    @Transactional(readOnly = true)
    public PageResult<ProcessInstanceVO> listHistoricInstances(
            String processKey, boolean finished, int pageNo, int pageSize) {
        var list = workflowEngine.listHistoricInstances(processKey, finished, pageNo, pageSize)
                .stream()
                .map(this::toInstanceVO)
                .toList();
        long total = workflowEngine.countHistoricInstances(processKey, finished);
        return new PageResult<>(list, total);
    }

    /** 挂起流程实例。 */
    @Transactional
    public void suspendInstance(String processInstanceId) {
        workflowEngine.suspendInstance(processInstanceId);
    }

    /** 激活流程实例。 */
    @Transactional
    public void activateInstance(String processInstanceId) {
        workflowEngine.activateInstance(processInstanceId);
    }

    /** 终止流程实例。 */
    @Transactional
    public void terminateInstance(String processInstanceId, String reason) {
        workflowEngine.terminateInstance(processInstanceId, reason);
    }

    /** 删除流程实例。 */
    @Transactional
    public void deleteInstance(String processInstanceId, String reason) {
        workflowEngine.deleteInstance(processInstanceId, reason);
    }

    /** 设置流程变量。 */
    @Transactional
    public void setProcessVariables(String processInstanceId, Map<String, Object> variables) {
        workflowEngine.setProcessVariables(processInstanceId, variables);
    }

    /** 获取流程变量。 */
    @Transactional(readOnly = true)
    public Map<String, Object> getProcessVariables(String processInstanceId) {
        return workflowEngine.getProcessVariables(processInstanceId);
    }

    // ==================== #5804 任务分配与流转 ====================

    /** 查询候选人待签收任务。 */
    @Transactional(readOnly = true)
    public List<WorkflowTaskVO> listCandidateTasks(String candidateUser) {
        return workflowEngine.listCandidateTasks(candidateUser).stream()
                .map(this::toTaskVO)
                .toList();
    }

    /** 查询候选组待签收任务。 */
    @Transactional(readOnly = true)
    public List<WorkflowTaskVO> listCandidateGroupTasks(String candidateGroup) {
        return workflowEngine.listCandidateGroupTasks(candidateGroup).stream()
                .map(this::toTaskVO)
                .toList();
    }

    /** 查询我发起的流程实例。 */
    @Transactional(readOnly = true)
    public PageResult<ProcessInstanceVO> listMyInitiatedInstances(
            String initiator, int pageNo, int pageSize) {
        var list = workflowEngine.listMyInitiatedInstances(initiator, pageNo, pageSize).stream()
                .map(this::toInstanceVO)
                .toList();
        // 简化：不单独 count，返回当前页数据量作为 total 的下界
        return new PageResult<>(list, list.size());
    }

    /** 签收任务。 */
    @Transactional
    public void claimTask(String taskId, String userId) {
        workflowEngine.claimTask(taskId, userId);
    }

    /** 委派任务。 */
    @Transactional
    public void delegateTask(String taskId, String delegateUserId) {
        workflowEngine.delegateTask(taskId, delegateUserId);
    }

    /** 退回任务。 */
    @Transactional
    public void returnTask(String taskId, String reason) {
        workflowEngine.returnTask(taskId, reason);
    }

    /** 催办任务。 */
    @Transactional
    public void urgeTask(String taskId, String urgerId) {
        workflowEngine.urgeTask(taskId, urgerId);
    }

    // ==================== #5805 信号与消息事件 ====================

    /** 发送信号事件。 */
    @Transactional
    public void sendSignal(String signalName, Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            workflowEngine.sendSignal(signalName);
        } else {
            workflowEngine.sendSignal(signalName, variables);
        }
    }

    /** 发送消息事件。 */
    @Transactional
    public void sendMessage(
            String messageName, String processInstanceId, Map<String, Object> variables) {
        workflowEngine.sendMessage(messageName, processInstanceId, variables);
    }

    // ==================== #6105 工作流发布与版本 ====================

    /** 发布工作流为可对话 Agent。 */
    @Transactional
    public void publishWorkflow(WorkflowPublishDTO dto) {
        // 验证流程定义存在
        var versions = workflowEngine.listDefinitionVersions(dto.processKey());
        if (versions.isEmpty()) {
            throw new com.xuejiai.aaf.common.exception.BusinessException(
                    com.xuejiai.aaf.common.exception.GlobalErrorCode.BAD_REQUEST,
                    "流程定义不存在: " + dto.processKey());
        }
        // TODO: 关联 processKey 到 Agent 注册表（待 Agent 模块完善后实现）
        log.info("工作流已发布为可对话 Agent: processKey={}, name={}", dto.processKey(), dto.name());
    }

    /** 查询工作流版本列表。 */
    @Transactional(readOnly = true)
    public List<WorkflowVersionVO> listVersions(String processKey) {
        return workflowEngine.listDefinitionVersions(processKey).stream()
                .map(d -> new WorkflowVersionVO(
                        d.processKey(), d.version(), d.name(),
                        d.id(), !d.suspended(), null))
                .toList();
    }

    /** 激活指定版本。 */
    @Transactional
    public void activateVersion(String processKey, int version) {
        var versions = workflowEngine.listDefinitionVersions(processKey);
        // 挂起所有版本，激活目标版本
        for (var v : versions) {
            if (v.version() == version) {
                if (v.suspended()) {
                    workflowEngine.activateDefinition(v.id());
                }
            } else {
                if (!v.suspended()) {
                    workflowEngine.suspendDefinition(v.id());
                }
            }
        }
    }

    // ==================== 内部转换 ====================

    private WorkflowTaskVO toTaskVO(WorkflowEngine.TaskInfo t) {
        return new WorkflowTaskVO(t.taskId(), t.processInstanceId(), t.name(), t.assignee());
    }

    private ProcessDefinitionVO toDefinitionVO(WorkflowEngine.DefinitionInfo d) {
        return new ProcessDefinitionVO(
                d.processKey(), d.name(), d.version(), d.id(), d.suspended());
    }

    private ProcessInstanceVO toInstanceVO(WorkflowEngine.InstanceInfo i) {
        return new ProcessInstanceVO(
                i.processInstanceId(),
                i.processKey(),
                i.businessKey(),
                i.status(),
                i.startTimeMs(),
                i.endTimeMs());
    }
}
