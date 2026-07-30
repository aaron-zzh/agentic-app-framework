package com.xuejiai.aaf.module.system.workflow.approval;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.workflow.WorkflowEngine;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.module.system.org.service.OrganizationService;
import com.xuejiai.aaf.module.system.role.service.RoleService;
import com.xuejiai.aaf.module.system.workflow.approval.ApprovalNodeConfig.EmptyAssigneeStrategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 审批节点服务——解析审批人策略、处理超时和空审批人。
 *
 * @author AaronZZH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private static final Pattern VARIABLE_EXPRESSION =
            Pattern.compile("^(?:\\$\\{|#\\{)?([A-Za-z_][A-Za-z0-9_.]*)(?:})?$");

    private final RoleService roleService;
    private final OrganizationService organizationService;
    private final WorkflowEngine workflowEngine;

    /**
     * 解析审批人策略，返回实际审批人列表。
     *
     * @param config 审批节点配置
     * @param processVariables 流程变量（用于表达式解析等）
     * @return 审批人标识列表
     */
    public List<String> resolveAssignees(
            ApprovalNodeConfig config, java.util.Map<String, Object> processVariables) {
        var assignees =
                switch (config.assigneeStrategy()) {
                    case FIXED_USER ->
                            config.assignees() != null ? config.assignees() : List.<String>of();
                    case ROLE -> resolveByRole(config.roleKey());
                    case DEPARTMENT_HEAD -> resolveDepartmentHead(processVariables);
                    case INITIATOR_SELECT -> resolveInitiatorSelect(processVariables);
                    case EXPRESSION -> resolveExpression(config.expression(), processVariables);
                };

        if (assignees.isEmpty()) {
            return handleEmptyAssignee(config.emptyAssigneeStrategy());
        }
        return assignees;
    }

    /**
     * 执行审批超时策略。
     *
     * @param config 审批节点配置
     * @param taskId 任务 ID
     */
    public void handleTimeout(ApprovalNodeConfig config, String taskId) {
        var strategy = config.timeoutStrategy();
        if (strategy == null) {
            log.debug("未配置超时策略：taskId={}", taskId);
            return;
        }
        log.info("审批超时处理：taskId={}, strategy={}", taskId, strategy);
        switch (strategy) {
            case AUTO_APPROVE ->
                    workflowEngine.completeTask(
                            taskId, Map.of("approved", true), "审批超时自动通过");
            case AUTO_REJECT ->
                    workflowEngine.completeTask(
                            taskId, Map.of("approved", false), "审批超时自动拒绝");
            case TRANSFER -> {
                var target =
                        config.assignees() == null || config.assignees().isEmpty()
                                ? null
                                : config.assignees().getFirst();
                if (target == null || target.isBlank()) {
                    throw new BusinessException(
                            GlobalErrorCode.BAD_REQUEST, "超时转交策略未配置目标审批人");
                }
                workflowEngine.transferSign(taskId, target, "审批超时自动转交");
            }
            case REMIND -> workflowEngine.urgeTask(taskId, "system-timeout");
        }
    }

    private List<String> handleEmptyAssignee(EmptyAssigneeStrategy strategy) {
        if (strategy == null) {
            strategy = EmptyAssigneeStrategy.ERROR;
        }
        return switch (strategy) {
            case SKIP -> List.of();
            case ADMIN -> List.of("admin");
            case ERROR ->
                    throw new BusinessException(
                            GlobalErrorCode.INTERNAL_SERVER_ERROR, "审批人为空，无法继续流程");
        };
    }

    private List<String> resolveByRole(String roleKey) {
        if (roleKey == null || roleKey.isBlank()) {
            return List.of();
        }
        return roleService.listActiveUserIdsByCode(roleKey).stream().map(String::valueOf).toList();
    }

    private List<String> resolveDepartmentHead(Map<String, Object> variables) {
        var configuredHead = variables.get("departmentHeadId");
        if (configuredHead != null) {
            return List.of(String.valueOf(configuredHead));
        }
        var orgId = OrgContext.getCurrentOrgId();
        if (orgId == null) {
            return List.of();
        }
        return List.of(String.valueOf(organizationService.getById(orgId).ownerId()));
    }

    private List<String> resolveInitiatorSelect(Map<String, Object> variables) {
        var selected = variables.get("selectedAssignees");
        return toAssignees(selected);
    }

    private List<String> resolveExpression(String expression, Map<String, Object> variables) {
        if (expression == null || expression.isBlank()) {
            return List.of();
        }
        var matcher = VARIABLE_EXPRESSION.matcher(expression.trim());
        if (!matcher.matches()) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "审批人表达式仅支持安全的变量路径");
        }
        Object value = variables;
        for (var segment : matcher.group(1).split("\\.")) {
            if (!(value instanceof Map<?, ?> map)) {
                return List.of();
            }
            value = map.get(segment);
        }
        return toAssignees(value);
    }

    private List<String> toAssignees(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(String::valueOf)
                    .filter(item -> !item.isBlank())
                    .distinct()
                    .toList();
        }
        var assignee = String.valueOf(value);
        return assignee.isBlank() ? List.of() : List.of(assignee);
    }
}
