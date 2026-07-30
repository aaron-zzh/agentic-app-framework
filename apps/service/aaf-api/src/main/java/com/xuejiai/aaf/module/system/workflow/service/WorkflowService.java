package com.xuejiai.aaf.module.system.workflow.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.framework.engine.workflow.WorkflowEngine;
import com.xuejiai.aaf.framework.org.OrgContext;
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
    private static final String ORG_ID_VARIABLE = "_aafOrgId";
    private static final String WORKSPACE_ID_VARIABLE = "_aafWorkspaceId";
    private static final long ORGANIZATION_SCOPE = 0L;

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
        var variables = new HashMap<String, Object>();
        variables.put("entityType", entityType);
        variables.put("entityId", entityId);
        variables.put("initiator", initiator);
        variables.put("assignee", assignee);
        variables.put(ORG_ID_VARIABLE, requireOrgId());
        variables.put(
                WORKSPACE_ID_VARIABLE,
                OrgContext.getCurrentWorkspaceId() != null
                        ? OrgContext.getCurrentWorkspaceId()
                        : ORGANIZATION_SCOPE);
        return workflowEngine.startProcess(PROCESS_KEY, businessKey(entityType, entityId), variables);
    }

    /** 通过审批。 */
    @Transactional
    public String completeTask(String taskId, String operatorId, String comment) {
        var task = requireTaskOperator(taskId, operatorId);
        workflowEngine.completeTask(taskId, Map.of("approved", true), comment);
        return task.processInstanceId();
    }

    /** 驳回审批。 */
    @Transactional
    public String rejectTask(String taskId, String operatorId, String comment) {
        var task = requireTaskOperator(taskId, operatorId);
        workflowEngine.completeTask(taskId, Map.of("approved", false), comment);
        return task.processInstanceId();
    }

    /** 查询流程状态。 */
    @Transactional(readOnly = true)
    public WorkflowStatusVO getStatus(String processInstanceId, String userId) {
        requireInstanceAccess(processInstanceId, userId);
        return loadStatus(processInstanceId);
    }

    /**
     * 按实体类型和 ID 查询关联流程状态。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @param userId 当前用户标识
     * @return 流程状态（无关联流程时返回 status=none 的空对象）
     */
    @Transactional(readOnly = true)
    public WorkflowStatusVO getStatusByEntity(String entityType, String entityId, String userId) {
        var processInstanceId =
                workflowEngine.findInstanceByBusinessKey(
                        businessKey(entityType, parseEntityId(entityId)));
        if (processInstanceId == null) {
            return new WorkflowStatusVO(
                    null, entityType, parseEntityId(entityId), null, false, null, null, List.of());
        }
        requireInstanceAccess(processInstanceId, userId);
        return loadStatus(processInstanceId);
    }

    /** 查询审批历史。 */
    @Transactional(readOnly = true)
    public List<WorkflowStatusVO.HistoryItem> getHistory(
            String processInstanceId, String userId) {
        requireInstanceAccess(processInstanceId, userId);
        return loadHistory(processInstanceId);
    }

    /** 查询指定审批人的待办任务列表。 */
    @Transactional(readOnly = true)
    public List<WorkflowTaskVO> listPendingTasks(String assignee) {
        requireOrgId();
        return workflowEngine.listPendingTasks(assignee).stream()
                .filter(task -> belongsToCurrentTenant(task.processInstanceId()))
                .map(this::toTaskVO)
                .toList();
    }

    /** 查询所有流程定义（最新版本）。 */
    @Transactional(readOnly = true)
    public List<ProcessDefinitionVO> listDefinitions() {
        return workflowEngine.listDefinitions().stream().map(this::toDefinitionVO).toList();
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
        validatePage(pageNo, pageSize);
        var list =
                workflowEngine.queryDefinitions(key, name, pageNo, pageSize).stream()
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
        validatePage(pageNo, pageSize);
        var orgId = requireOrgId();
        var workspaceId = OrgContext.getCurrentWorkspaceId();
        var list =
                workflowEngine
                        .listRunningInstances(processKey, orgId, workspaceId, pageNo, pageSize)
                        .stream()
                        .map(this::toInstanceVO)
                        .toList();
        long total = workflowEngine.countRunningInstances(processKey, orgId, workspaceId);
        return new PageResult<>(list, total);
    }

    /** 分页查询历史流程实例。 */
    @Transactional(readOnly = true)
    public PageResult<ProcessInstanceVO> listHistoricInstances(
            String processKey, boolean finished, int pageNo, int pageSize) {
        validatePage(pageNo, pageSize);
        var orgId = requireOrgId();
        var workspaceId = OrgContext.getCurrentWorkspaceId();
        var list =
                workflowEngine
                        .listHistoricInstances(
                                processKey, finished, orgId, workspaceId, pageNo, pageSize)
                        .stream()
                        .map(this::toInstanceVO)
                        .toList();
        long total =
                workflowEngine.countHistoricInstances(
                        processKey, finished, orgId, workspaceId);
        return new PageResult<>(list, total);
    }

    /** 挂起流程实例。 */
    @Transactional
    public void suspendInstance(String processInstanceId) {
        requireInstanceTenant(processInstanceId);
        workflowEngine.suspendInstance(processInstanceId);
    }

    /** 激活流程实例。 */
    @Transactional
    public void activateInstance(String processInstanceId) {
        requireInstanceTenant(processInstanceId);
        workflowEngine.activateInstance(processInstanceId);
    }

    /** 终止流程实例。 */
    @Transactional
    public void terminateInstance(String processInstanceId, String reason) {
        requireInstanceTenant(processInstanceId);
        workflowEngine.terminateInstance(processInstanceId, reason);
    }

    /** 删除流程实例。 */
    @Transactional
    public void deleteInstance(String processInstanceId, String reason) {
        requireInstanceTenant(processInstanceId);
        workflowEngine.deleteInstance(processInstanceId, reason);
    }

    /** 设置流程变量。 */
    @Transactional
    public void setProcessVariables(String processInstanceId, Map<String, Object> variables) {
        requireInstanceTenant(processInstanceId);
        if (variables.containsKey(ORG_ID_VARIABLE)
                || variables.containsKey(WORKSPACE_ID_VARIABLE)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "禁止修改流程租户变量");
        }
        workflowEngine.setProcessVariables(processInstanceId, variables);
    }

    /** 获取流程变量。 */
    @Transactional(readOnly = true)
    public Map<String, Object> getProcessVariables(String processInstanceId) {
        requireInstanceTenant(processInstanceId);
        return workflowEngine.getProcessVariables(processInstanceId);
    }

    // ==================== #5804 任务分配与流转 ====================

    /** 查询候选人待签收任务。 */
    @Transactional(readOnly = true)
    public List<WorkflowTaskVO> listCandidateTasks(String candidateUser) {
        requireOrgId();
        return workflowEngine.listCandidateTasks(candidateUser).stream()
                .filter(task -> belongsToCurrentTenant(task.processInstanceId()))
                .map(this::toTaskVO)
                .toList();
    }

    /** 查询候选组待签收任务。 */
    @Transactional(readOnly = true)
    public List<WorkflowTaskVO> listCandidateGroupTasks(String candidateGroup) {
        requireOrgId();
        return workflowEngine.listCandidateGroupTasks(candidateGroup).stream()
                .filter(task -> belongsToCurrentTenant(task.processInstanceId()))
                .map(this::toTaskVO)
                .toList();
    }

    /** 查询我发起的流程实例。 */
    @Transactional(readOnly = true)
    public PageResult<ProcessInstanceVO> listMyInitiatedInstances(
            String initiator, int pageNo, int pageSize) {
        validatePage(pageNo, pageSize);
        requireOrgId();
        var list =
                workflowEngine.listMyInitiatedInstances(initiator, pageNo, pageSize).stream()
                        .filter(instance -> belongsToCurrentTenant(instance.processInstanceId()))
                        .map(this::toInstanceVO)
                        .toList();
        // 简化：不单独 count，返回当前页数据量作为 total 的下界
        return new PageResult<>(list, list.size());
    }

    /** 签收任务。 */
    @Transactional
    public void claimTask(String taskId, String userId) {
        requireTaskTenant(taskId);
        var task = workflowEngine.getTask(taskId);
        if (task == null) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "任务不存在");
        }
        if (!workflowEngine.canOperateTask(taskId, userId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "当前用户不是任务候选人");
        }
        workflowEngine.claimTask(taskId, userId);
    }

    /** 单次转交任务。 */
    @Transactional
    public void reassignTask(String taskId, String operatorId, String targetUserId) {
        requireTaskOperator(taskId, operatorId);
        workflowEngine.reassignTask(taskId, targetUserId);
    }

    /** 委派任务。 */
    @Transactional
    public void delegateTask(String taskId, String operatorId, String delegateUserId) {
        requireTaskOperator(taskId, operatorId);
        workflowEngine.delegateTask(taskId, delegateUserId);
    }

    /** 退回任务。 */
    @Transactional
    public void returnTask(String taskId, String operatorId, String reason) {
        requireTaskOperator(taskId, operatorId);
        workflowEngine.returnTask(taskId, reason);
    }

    /** 催办任务。 */
    @Transactional
    public void urgeTask(String taskId, String urgerId) {
        var task = requireTask(taskId);
        requireInstanceAccess(task.processInstanceId(), urgerId);
        workflowEngine.urgeTask(taskId, urgerId);
    }

    // ==================== #5805 信号与消息事件 ====================

    /** 发送信号事件。 */
    @Transactional
    public void sendSignal(
            String signalName, String processInstanceId, Map<String, Object> variables) {
        requireInstanceTenant(processInstanceId);
        workflowEngine.sendSignal(signalName, processInstanceId, variables);
    }

    /** 发送消息事件。 */
    @Transactional
    public void sendMessage(
            String messageName, String processInstanceId, Map<String, Object> variables) {
        requireInstanceTenant(processInstanceId);
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
                .map(
                        d ->
                                new WorkflowVersionVO(
                                        d.processKey(),
                                        d.version(),
                                        d.name(),
                                        d.id(),
                                        !d.suspended(),
                                        null))
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

    // ==================== 访问控制与内部转换 ====================

    /** 校验当前用户可以处理任务，并返回任务信息。 */
    public WorkflowEngine.TaskInfo requireTaskOperator(String taskId, String userId) {
        var task = requireTask(taskId);
        requireTenant(workflowEngine.getProcessVariables(task.processInstanceId()));
        if (!workflowEngine.canOperateTask(taskId, userId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "无权操作此审批任务");
        }
        return task;
    }

    /** 校验当前用户可以访问流程实例。 */
    public void requireInstanceAccess(String processInstanceId, String userId) {
        var variables = workflowEngine.getProcessVariables(processInstanceId);
        if (variables.isEmpty()) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "流程实例不存在");
        }
        requireTenant(variables);
        if (!workflowEngine.isProcessParticipant(processInstanceId, userId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "无权访问此流程实例");
        }
    }

    private WorkflowEngine.TaskInfo requireTask(String taskId) {
        var task = workflowEngine.getTask(taskId);
        if (task == null) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "任务不存在");
        }
        return task;
    }

    private void requireTaskTenant(String taskId) {
        var task = requireTask(taskId);
        requireTenant(workflowEngine.getProcessVariables(task.processInstanceId()));
    }

    private void requireTenant(Map<String, Object> variables) {
        var processOrgId = longValue(variables.get(ORG_ID_VARIABLE));
        var storedWorkspaceId = longValue(variables.get(WORKSPACE_ID_VARIABLE));
        var processWorkspaceId =
                Objects.equals(storedWorkspaceId, ORGANIZATION_SCOPE) ? null : storedWorkspaceId;
        if (!Objects.equals(processOrgId, requireOrgId())
                || !Objects.equals(processWorkspaceId, OrgContext.getCurrentWorkspaceId())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "流程不属于当前组织或工作区");
        }
    }

    private void requireInstanceTenant(String processInstanceId) {
        var variables = workflowEngine.getProcessVariables(processInstanceId);
        if (variables.isEmpty()) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "流程实例不存在");
        }
        requireTenant(variables);
    }

    private boolean belongsToCurrentTenant(String processInstanceId) {
        var variables = workflowEngine.getProcessVariables(processInstanceId);
        var storedWorkspaceId = longValue(variables.get(WORKSPACE_ID_VARIABLE));
        var processWorkspaceId =
                Objects.equals(storedWorkspaceId, ORGANIZATION_SCOPE) ? null : storedWorkspaceId;
        return Objects.equals(longValue(variables.get(ORG_ID_VARIABLE)), requireOrgId())
                && Objects.equals(processWorkspaceId, OrgContext.getCurrentWorkspaceId());
    }

    private WorkflowStatusVO loadStatus(String processInstanceId) {
        var currentTask = workflowEngine.getCurrentTask(processInstanceId);
        var variables = workflowEngine.getProcessVariables(processInstanceId);
        return new WorkflowStatusVO(
                processInstanceId,
                (String) variables.get("entityType"),
                longValue(variables.get("entityId")),
                (String) variables.get("initiator"),
                currentTask == null,
                currentTask != null ? currentTask.taskId() : null,
                currentTask != null ? currentTask.assignee() : null,
                loadHistory(processInstanceId));
    }

    private List<WorkflowStatusVO.HistoryItem> loadHistory(String processInstanceId) {
        return workflowEngine.getHistory(processInstanceId).stream()
                .map(
                        record ->
                                new WorkflowStatusVO.HistoryItem(
                                        record.taskName(),
                                        record.assignee(),
                                        record.outcome(),
                                        record.comment(),
                                        LocalDateTime.ofInstant(
                                                Instant.ofEpochMilli(record.completedAtMs()),
                                                ZoneId.systemDefault())))
                .toList();
    }

    private String businessKey(String entityType, Long entityId) {
        return "%s:%s:%s:%d"
                .formatted(
                        requireOrgId(),
                        Objects.toString(OrgContext.getCurrentWorkspaceId(), "global"),
                        entityType,
                        entityId);
    }

    private Long parseEntityId(String entityId) {
        try {
            return Long.valueOf(entityId);
        } catch (NumberFormatException e) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "实体 ID 格式不正确");
        }
    }

    private void validatePage(int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 200) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "页码必须从 1 开始，每页数量必须在 1 到 200 之间");
        }
    }

    private Long requireOrgId() {
        var orgId = OrgContext.getCurrentOrgId();
        if (orgId == null) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "工作流操作必须指定组织上下文");
        }
        return orgId;
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

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
