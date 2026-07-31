package com.xuejiai.aaf.module.approval.service;

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
import com.xuejiai.aaf.framework.crud.reference.EntityReferenceAccess;
import com.xuejiai.aaf.framework.crud.reference.ResourceReference;
import com.xuejiai.aaf.framework.engine.bpmn.api.BpmnEngine;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.module.approval.api.ApprovalProcessApi;
import com.xuejiai.aaf.module.approval.vo.ApprovalProcessInstanceVO;
import com.xuejiai.aaf.module.approval.vo.WorkflowStatusVO;
import com.xuejiai.aaf.module.approval.vo.WorkflowTaskVO;

import lombok.RequiredArgsConstructor;

/** 审批流程服务。 */
@Service
@RequiredArgsConstructor
public class ApprovalProcessService implements ApprovalProcessApi {

    private static final String PROCESS_KEY = "generic-approval";
    private static final String ORG_ID_VARIABLE = "_aafOrgId";
    private static final String WORKSPACE_ID_VARIABLE = "_aafWorkspaceId";
    private static final long ORGANIZATION_SCOPE = 0L;

    private final BpmnEngine bpmnEngine;
    private final ApprovalOperationService approvalOperationService;
    private final EntityReferenceAccess entityReferenceAccess;

    /** 启动审批流程。 */
    @Override
    @Transactional
    public String startProcess(
            String entityType, Long entityId, String initiator, String assignee) {
        entityReferenceAccess.requireReadable(
                new ResourceReference(entityType, entityId), "审批目标");
        var variables = new HashMap<String, Object>();
        variables.put("entityType", entityType);
        variables.put("entityId", entityId);
        variables.put("initiator", initiator);
        variables.put("assignees", List.of(assignee));
        variables.put(ORG_ID_VARIABLE, requireOrgId());
        variables.put(
                WORKSPACE_ID_VARIABLE,
                OrgContext.getCurrentWorkspaceId() != null
                        ? OrgContext.getCurrentWorkspaceId()
                        : ORGANIZATION_SCOPE);
        return bpmnEngine.startProcess(
                PROCESS_KEY, businessKey(entityType, entityId), variables);
    }

    /** 通过审批。 */
    @Transactional
    public String completeTask(String taskId, String operatorId, String comment) {
        var task = requireTaskOperator(taskId, operatorId);
        bpmnEngine.completeTask(taskId, Map.of("approved", true), comment);
        return task.processInstanceId();
    }

    /** 驳回审批。 */
    @Transactional
    public String rejectTask(String taskId, String operatorId, String comment) {
        var task = requireTaskOperator(taskId, operatorId);
        bpmnEngine.completeTask(taskId, Map.of("approved", false), comment);
        return task.processInstanceId();
    }

    /** 查询流程状态。 */
    @Transactional(readOnly = true)
    public WorkflowStatusVO getStatus(String processInstanceId, String userId) {
        requireInstanceAccess(processInstanceId, userId);
        return loadStatus(processInstanceId);
    }

    /** 按实体类型和 ID 查询关联流程状态。 */
    @Transactional(readOnly = true)
    public WorkflowStatusVO getStatusByEntity(String entityType, String entityId, String userId) {
        var parsedEntityId = parseEntityId(entityId);
        var processInstanceId =
                bpmnEngine.findInstanceByBusinessKey(
                        businessKey(entityType, parsedEntityId));
        if (processInstanceId == null) {
            return new WorkflowStatusVO(
                    null, entityType, parsedEntityId, null, false, null, null, List.of());
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
        return bpmnEngine.listPendingTasks(assignee).stream()
                .filter(task -> belongsToCurrentTenant(task.processInstanceId()))
                .map(this::toTaskVO)
                .toList();
    }

    /** 查询候选人待签收任务。 */
    @Transactional(readOnly = true)
    public List<WorkflowTaskVO> listCandidateTasks(String candidateUser) {
        requireOrgId();
        return bpmnEngine.listCandidateTasks(candidateUser).stream()
                .filter(task -> belongsToCurrentTenant(task.processInstanceId()))
                .map(this::toTaskVO)
                .toList();
    }

    /** 查询候选组待签收任务。 */
    @Transactional(readOnly = true)
    public List<WorkflowTaskVO> listCandidateGroupTasks(String candidateGroup) {
        requireOrgId();
        return bpmnEngine.listCandidateGroupTasks(candidateGroup).stream()
                .filter(task -> belongsToCurrentTenant(task.processInstanceId()))
                .map(this::toTaskVO)
                .toList();
    }

    /** 查询我发起的流程实例。 */
    @Transactional(readOnly = true)
    public PageResult<ApprovalProcessInstanceVO> listMyInitiatedInstances(
            String initiator, int pageNo, int pageSize) {
        validatePage(pageNo, pageSize);
        var variableEquals =
                Map.<String, Object>of(
                        "initiator", initiator,
                        ORG_ID_VARIABLE, requireOrgId(),
                        WORKSPACE_ID_VARIABLE,
                                OrgContext.getCurrentWorkspaceId() != null
                                        ? OrgContext.getCurrentWorkspaceId()
                                        : ORGANIZATION_SCOPE);
        var list =
                bpmnEngine.listInstances(PROCESS_KEY, variableEquals, pageNo, pageSize).stream()
                        .map(this::toInstanceVO)
                        .toList();
        var total = bpmnEngine.countInstances(PROCESS_KEY, variableEquals);
        return new PageResult<>(list, total);
    }

    /** 签收任务。 */
    @Transactional
    public void claimTask(String taskId, String userId) {
        requireTaskTenant(taskId);
        var task = bpmnEngine.getTask(taskId);
        if (task == null) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "任务不存在");
        }
        if (!bpmnEngine.canOperateTask(taskId, userId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "当前用户不是任务候选人");
        }
        bpmnEngine.claimTask(taskId, userId);
    }

    /** 单次转交任务。 */
    @Transactional
    public void reassignTask(String taskId, String operatorId, String targetUserId) {
        requireTaskOperator(taskId, operatorId);
        bpmnEngine.reassignTask(taskId, targetUserId);
    }

    /** 委派任务。 */
    @Transactional
    public void delegateTask(String taskId, String operatorId, String delegateUserId) {
        requireTaskOperator(taskId, operatorId);
        bpmnEngine.delegateTask(taskId, delegateUserId);
    }

    /** 退回任务。 */
    @Transactional
    public void returnTask(String taskId, String operatorId, String reason) {
        requireTaskOperator(taskId, operatorId);
        approvalOperationService.returnTask(taskId, reason);
    }

    /** 催办任务。 */
    @Transactional
    public void urgeTask(String taskId, String urgerId) {
        var task = requireTask(taskId);
        requireInstanceAccess(task.processInstanceId(), urgerId);
        approvalOperationService.urgeTask(taskId, urgerId);
    }

    /** 校验当前用户可以处理审批任务，并返回任务信息。 */
    public BpmnEngine.TaskInfo requireTaskOperator(String taskId, String userId) {
        var task = requireTask(taskId);
        requireTenant(bpmnEngine.getProcessVariables(task.processInstanceId()));
        if (!bpmnEngine.canOperateTask(taskId, userId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "无权操作此审批任务");
        }
        return task;
    }

    /** 校验当前用户可以访问审批流程实例。 */
    public void requireInstanceAccess(String processInstanceId, String userId) {
        var variables = bpmnEngine.getProcessVariables(processInstanceId);
        if (variables.isEmpty()) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "流程实例不存在");
        }
        requireTenant(variables);
        var isInitiator = userId.equals(Objects.toString(variables.get("initiator"), null));
        if (!isInitiator && !bpmnEngine.hasTaskParticipant(processInstanceId, userId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "无权访问此流程实例");
        }
    }

    private BpmnEngine.TaskInfo requireTask(String taskId) {
        var task = bpmnEngine.getTask(taskId);
        if (task == null) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "任务不存在");
        }
        return task;
    }

    private void requireTaskTenant(String taskId) {
        var task = requireTask(taskId);
        requireTenant(bpmnEngine.getProcessVariables(task.processInstanceId()));
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

    private boolean belongsToCurrentTenant(String processInstanceId) {
        var variables = bpmnEngine.getProcessVariables(processInstanceId);
        var storedWorkspaceId = longValue(variables.get(WORKSPACE_ID_VARIABLE));
        var processWorkspaceId =
                Objects.equals(storedWorkspaceId, ORGANIZATION_SCOPE) ? null : storedWorkspaceId;
        return Objects.equals(longValue(variables.get(ORG_ID_VARIABLE)), requireOrgId())
                && Objects.equals(processWorkspaceId, OrgContext.getCurrentWorkspaceId());
    }

    private WorkflowStatusVO loadStatus(String processInstanceId) {
        var currentTask = bpmnEngine.getCurrentTask(processInstanceId);
        var variables = bpmnEngine.getProcessVariables(processInstanceId);
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
        return bpmnEngine.getHistory(processInstanceId).stream()
                .map(
                        record ->
                                new WorkflowStatusVO.HistoryItem(
                                        record.taskName(),
                                        record.assignee(),
                                        approvalOutcome(record.variables()),
                                        record.comment(),
                                        LocalDateTime.ofInstant(
                                                Instant.ofEpochMilli(record.completedAtMs()),
                                                ZoneId.systemDefault())))
                .toList();
    }

    private String approvalOutcome(Map<String, Object> variables) {
        var approved = variables.get("approved");
        if (Boolean.TRUE.equals(approved)) {
            return "通过";
        }
        return Boolean.FALSE.equals(approved) ? "驳回" : null;
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

    private WorkflowTaskVO toTaskVO(BpmnEngine.TaskInfo task) {
        return new WorkflowTaskVO(
                task.taskId(), task.processInstanceId(), task.name(), task.assignee());
    }

    private ApprovalProcessInstanceVO toInstanceVO(BpmnEngine.InstanceInfo instance) {
        return new ApprovalProcessInstanceVO(
                instance.processInstanceId(),
                instance.processKey(),
                instance.businessKey(),
                instance.status(),
                instance.startTimeMs(),
                instance.endTimeMs());
    }
}
