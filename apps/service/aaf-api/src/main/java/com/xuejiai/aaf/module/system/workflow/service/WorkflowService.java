package com.xuejiai.aaf.module.system.workflow.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.framework.engine.bpmn.api.BpmnEngine;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.module.system.workflow.vo.ProcessDefinitionVO;
import com.xuejiai.aaf.module.system.workflow.vo.ProcessInstanceVO;
import com.xuejiai.aaf.module.system.workflow.vo.WorkflowDeployDTO;
import com.xuejiai.aaf.module.system.workflow.vo.WorkflowPublishDTO;
import com.xuejiai.aaf.module.system.workflow.vo.WorkflowVersionVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作流服务，委托给 BpmnEngine 引擎层接口。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private static final String ORG_ID_VARIABLE = "_aafOrgId";
    private static final String WORKSPACE_ID_VARIABLE = "_aafWorkspaceId";
    private static final long ORGANIZATION_SCOPE = 0L;

    private final BpmnEngine bpmnEngine;

    // ==================== #5802 流程定义管理 ====================

    /** 查询所有流程定义（最新版本）。 */
    @Transactional(readOnly = true)
    public List<ProcessDefinitionVO> listDefinitions() {
        return bpmnEngine.listDefinitions().stream().map(this::toDefinitionVO).toList();
    }

    /** 部署流程定义。 */
    @Transactional
    public String deployDefinition(WorkflowDeployDTO dto) {
        return bpmnEngine.deploy(dto.name(), dto.bpmnXml());
    }

    /** 分页查询流程定义。 */
    @Transactional(readOnly = true)
    public PageResult<ProcessDefinitionVO> queryDefinitions(
            String key, String name, int pageNo, int pageSize) {
        validatePage(pageNo, pageSize);
        var list =
                bpmnEngine.queryDefinitions(key, name, pageNo, pageSize).stream()
                        .map(this::toDefinitionVO)
                        .toList();
        long total = bpmnEngine.countDefinitions(key, name);
        return new PageResult<>(list, total);
    }

    /** 查询指定 key 的所有版本。 */
    @Transactional(readOnly = true)
    public List<ProcessDefinitionVO> listDefinitionVersions(String processKey) {
        return bpmnEngine.listDefinitionVersions(processKey).stream()
                .map(this::toDefinitionVO)
                .toList();
    }

    /** 挂起流程定义。 */
    @Transactional
    public void suspendDefinition(String processDefinitionId) {
        bpmnEngine.suspendDefinition(processDefinitionId);
    }

    /** 激活流程定义。 */
    @Transactional
    public void activateDefinition(String processDefinitionId) {
        bpmnEngine.activateDefinition(processDefinitionId);
    }

    /** 删除流程定义。 */
    @Transactional
    public void deleteDeployment(String deploymentId, boolean cascade) {
        bpmnEngine.deleteDeployment(deploymentId, cascade);
    }

    /** 导出流程定义 XML。 */
    @Transactional(readOnly = true)
    public String exportDefinitionXml(String processDefinitionId) {
        return bpmnEngine.exportDefinitionXml(processDefinitionId);
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
                bpmnEngine
                        .listRunningInstances(processKey, orgId, workspaceId, pageNo, pageSize)
                        .stream()
                        .map(this::toInstanceVO)
                        .toList();
        long total = bpmnEngine.countRunningInstances(processKey, orgId, workspaceId);
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
                bpmnEngine
                        .listHistoricInstances(
                                processKey, finished, orgId, workspaceId, pageNo, pageSize)
                        .stream()
                        .map(this::toInstanceVO)
                        .toList();
        long total = bpmnEngine.countHistoricInstances(processKey, finished, orgId, workspaceId);
        return new PageResult<>(list, total);
    }

    /** 挂起流程实例。 */
    @Transactional
    public void suspendInstance(String processInstanceId) {
        requireInstanceTenant(processInstanceId);
        bpmnEngine.suspendInstance(processInstanceId);
    }

    /** 激活流程实例。 */
    @Transactional
    public void activateInstance(String processInstanceId) {
        requireInstanceTenant(processInstanceId);
        bpmnEngine.activateInstance(processInstanceId);
    }

    /** 终止流程实例。 */
    @Transactional
    public void terminateInstance(String processInstanceId, String reason) {
        requireInstanceTenant(processInstanceId);
        bpmnEngine.terminateInstance(processInstanceId, reason);
    }

    /** 删除流程实例。 */
    @Transactional
    public void deleteInstance(String processInstanceId, String reason) {
        requireInstanceTenant(processInstanceId);
        bpmnEngine.deleteInstance(processInstanceId, reason);
    }

    /** 设置流程变量。 */
    @Transactional
    public void setProcessVariables(String processInstanceId, Map<String, Object> variables) {
        requireInstanceTenant(processInstanceId);
        if (variables.containsKey(ORG_ID_VARIABLE)
                || variables.containsKey(WORKSPACE_ID_VARIABLE)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "禁止修改流程租户变量");
        }
        bpmnEngine.setProcessVariables(processInstanceId, variables);
    }

    /** 获取流程变量。 */
    @Transactional(readOnly = true)
    public Map<String, Object> getProcessVariables(String processInstanceId) {
        requireInstanceTenant(processInstanceId);
        return bpmnEngine.getProcessVariables(processInstanceId);
    }

    // ==================== #5805 信号与消息事件 ====================

    /** 发送信号事件。 */
    @Transactional
    public void sendSignal(
            String signalName, String processInstanceId, Map<String, Object> variables) {
        requireInstanceTenant(processInstanceId);
        bpmnEngine.sendSignal(signalName, processInstanceId, variables);
    }

    /** 发送消息事件。 */
    @Transactional
    public void sendMessage(
            String messageName, String processInstanceId, Map<String, Object> variables) {
        requireInstanceTenant(processInstanceId);
        bpmnEngine.sendMessage(messageName, processInstanceId, variables);
    }

    // ==================== #6105 工作流发布与版本 ====================

    /** 发布工作流为可对话 Agent。 */
    @Transactional
    public void publishWorkflow(WorkflowPublishDTO dto) {
        // 验证流程定义存在
        var versions = bpmnEngine.listDefinitionVersions(dto.processKey());
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
        return bpmnEngine.listDefinitionVersions(processKey).stream()
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
        var versions = bpmnEngine.listDefinitionVersions(processKey);
        // 挂起所有版本，激活目标版本
        for (var v : versions) {
            if (v.version() == version) {
                if (v.suspended()) {
                    bpmnEngine.activateDefinition(v.id());
                }
            } else {
                if (!v.suspended()) {
                    bpmnEngine.suspendDefinition(v.id());
                }
            }
        }
    }

    // ==================== 访问控制与内部转换 ====================

    /** 校验当前用户可以访问流程实例。 */
    public void requireInstanceAccess(String processInstanceId, String userId) {
        var variables = bpmnEngine.getProcessVariables(processInstanceId);
        if (variables.isEmpty()) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "流程实例不存在");
        }
        requireTenant(variables);
        var isStarter = userId.equals(Objects.toString(variables.get("_aafUserId"), null));
        if (!isStarter && !bpmnEngine.hasTaskParticipant(processInstanceId, userId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "无权访问此流程实例");
        }
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
        var variables = bpmnEngine.getProcessVariables(processInstanceId);
        if (variables.isEmpty()) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "流程实例不存在");
        }
        requireTenant(variables);
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

    private ProcessDefinitionVO toDefinitionVO(BpmnEngine.DefinitionInfo d) {
        return new ProcessDefinitionVO(
                d.processKey(), d.name(), d.version(), d.id(), d.suspended());
    }

    private ProcessInstanceVO toInstanceVO(BpmnEngine.InstanceInfo i) {
        return new ProcessInstanceVO(
                i.processInstanceId(),
                i.processKey(),
                i.businessKey(),
                i.status(),
                i.startTimeMs(),
                i.endTimeMs());
    }
}
