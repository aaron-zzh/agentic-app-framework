package com.xuejiai.aaf.module.ai.flow.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.bpmn.api.BpmnEngine;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.flow.domain.AiFlowDefinition;
import com.xuejiai.aaf.module.ai.flow.repository.AiFlowDefinitionRepository;

import lombok.RequiredArgsConstructor;

/** AI Flow 外部触发入口，集中执行身份、租户、发布状态和启动变量校验。 */
@Service
@RequiredArgsConstructor
public class AiFlowTriggerService {

    private static final String PUBLISHED = "PUBLISHED";
    private static final String RESERVED_VARIABLE_PREFIX = "_aaf";

    private final AiFlowDefinitionRepository flowRepository;
    private final AiFlowBpmnCompiler bpmnCompiler;
    private final BpmnEngine bpmnEngine;
    private final OperatorContext operatorContext;

    /** 从当前认证和组织上下文构造可信身份；任一必要上下文缺失时拒绝。 */
    public TrustedIdentity currentIdentity() {
        if (!operatorContext.isAuthenticated()) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED, "用户未登录");
        }
        var userId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.UNAUTHORIZED, "缺少当前用户身份"));
        var orgId = OrgContext.getCurrentOrgId();
        var workspaceId = normalizeWorkspaceId(OrgContext.getCurrentWorkspaceId());
        return new TrustedIdentity(userId, orgId, workspaceId);
    }

    /** 注册后台触发器前立即验证流程和变量，避免注册不可执行任务。 */
    public void validateTriggerable(
            Long flowId, TrustedIdentity identity, Map<String, Object> userVariables) {
        requireUserVariables(userVariables);
        requireTriggerableFlow(flowId, identity);
    }

    /** 以当前 Webhook 请求身份启动已发布 AI Flow。 */
    public TriggerResult triggerWebhook(
            Long flowId, Map<String, Object> userVariables, TrustedIdentity identity) {
        return trigger(flowId, "webhook", null, userVariables, identity);
    }

    /** 以定时任务注册时捕获的身份启动已发布 AI Flow。 */
    public TriggerResult triggerCron(
            Long flowId,
            String triggerId,
            Map<String, Object> userVariables,
            TrustedIdentity identity) {
        if (triggerId == null || triggerId.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "触发器 ID 不能为空");
        }
        return trigger(flowId, "cron", triggerId.trim(), userVariables, identity);
    }

    private TriggerResult trigger(
            Long flowId,
            String triggerType,
            String triggerId,
            Map<String, Object> userVariables,
            TrustedIdentity identity) {
        requireUserVariables(userVariables);
        var flow = requireTriggerableFlow(flowId, identity);
        var variables = new HashMap<String, Object>();
        if (userVariables != null) {
            variables.putAll(userVariables);
        }
        variables.put("_aafUserId", identity.userId());
        variables.put("_aafOrgId", identity.orgId());
        variables.put("_aafWorkspaceId", identity.workspaceId());

        var businessKey = businessKey(triggerType, triggerId, flow.getId());
        var processInstanceId =
                bpmnEngine.startProcess(
                        bpmnCompiler.processKey(flow.getId()), businessKey, variables);
        return new TriggerResult(
                flow.getId(), flow.getName(), processInstanceId, businessKey);
    }

    private AiFlowDefinition requireTriggerableFlow(Long flowId, TrustedIdentity identity) {
        if (flowId == null || flowId <= 0) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "AI Flow ID 必须大于 0");
        }
        if (identity == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED, "缺少可信触发身份");
        }

        Specification<AiFlowDefinition> specification =
                (root, query, criteriaBuilder) -> {
                    var workspacePredicate =
                            identity.workspaceId() == 0
                                    ? criteriaBuilder.or(
                                            criteriaBuilder.isNull(root.get("workspaceId")),
                                            criteriaBuilder.equal(root.get("workspaceId"), 0L))
                                    : criteriaBuilder.equal(
                                            root.get("workspaceId"), identity.workspaceId());
                    return criteriaBuilder.and(
                            criteriaBuilder.equal(root.get("id"), flowId),
                            criteriaBuilder.equal(root.get("orgId"), identity.orgId()),
                            workspacePredicate,
                            criteriaBuilder.equal(root.get("status"), PUBLISHED),
                            criteriaBuilder.equal(root.get("deleted"), false),
                            criteriaBuilder.isNotNull(root.get("deploymentId")),
                            criteriaBuilder.notEqual(
                                    criteriaBuilder.trim(root.<String>get("deploymentId")), ""));
                };
        return flowRepository
                .findOne(specification)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        GlobalErrorCode.NOT_FOUND, "AI Flow 不存在"));
    }

    private void requireUserVariables(Map<String, Object> userVariables) {
        if (userVariables == null) {
            return;
        }
        var containsReservedVariable =
                userVariables.keySet().stream()
                        .anyMatch(
                                key ->
                                        key != null
                                                && key.startsWith(RESERVED_VARIABLE_PREFIX));
        if (containsReservedVariable) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "禁止设置 AI Flow 保留变量");
        }
    }

    private String businessKey(String triggerType, String triggerId, Long flowId) {
        var triggerPart = triggerId != null ? triggerId : "flow-" + flowId;
        return "%s:%s:flow-%d:%s"
                .formatted(triggerType, triggerPart, flowId, UUID.randomUUID());
    }

    private static Long normalizeWorkspaceId(Long workspaceId) {
        return workspaceId == null ? 0L : workspaceId;
    }

    /** 已认证且绑定组织范围的不可变触发身份。 */
    public record TrustedIdentity(Long userId, Long orgId, Long workspaceId) {
        public TrustedIdentity {
            if (userId == null || userId <= 0) {
                throw new BusinessException(GlobalErrorCode.UNAUTHORIZED, "当前用户身份无效");
            }
            if (orgId == null || orgId <= 0) {
                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "缺少有效组织上下文");
            }
            if (workspaceId == null || workspaceId < 0) {
                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "工作区上下文无效");
            }
        }
    }

    /** AI Flow 触发结果。 */
    public record TriggerResult(
            Long flowId, String flowName, String processInstanceId, String businessKey) {}
}
