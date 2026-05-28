package com.xuejiai.aaf.module.system.workflow.approval;

import java.util.List;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.system.workflow.approval.ApprovalNodeConfig.AssigneeStrategy;
import com.xuejiai.aaf.module.system.workflow.approval.ApprovalNodeConfig.EmptyAssigneeStrategy;
import com.xuejiai.aaf.module.system.workflow.approval.ApprovalNodeConfig.TimeoutStrategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 审批节点服务——解析审批人策略、处理超时和空审批人。
 *
 * @author Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

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
                    case FIXED_USER -> config.assignees() != null ? config.assignees() : List.<String>of();
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
     * 处理超时逻辑（留接口，不实现定时器）。
     *
     * @param config 审批节点配置
     * @param taskId 任务 ID
     */
    public void handleTimeout(ApprovalNodeConfig config, String taskId) {
        log.info("审批超时处理：taskId={}, strategy={}", taskId, config.timeoutStrategy());
        switch (config.timeoutStrategy()) {
            case AUTO_APPROVE -> log.info("超时自动通过：taskId={}", taskId);
            case AUTO_REJECT -> log.info("超时自动拒绝：taskId={}", taskId);
            case TRANSFER -> log.info("超时转交：taskId={}", taskId);
            case REMIND -> log.info("超时提醒：taskId={}", taskId);
            case null -> log.debug("未配置超时策略：taskId={}", taskId);
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
                    throw new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR, "审批人为空，无法继续流程");
        };
    }

    private List<String> resolveByRole(String roleKey) {
        // TODO: 对接角色服务查询角色下用户
        log.debug("按角色解析审批人：roleKey={}", roleKey);
        return List.of();
    }

    private List<String> resolveDepartmentHead(java.util.Map<String, Object> variables) {
        // TODO: 对接组织架构查询部门主管
        var initiator = (String) variables.getOrDefault("initiator", "");
        log.debug("解析部门主管：initiator={}", initiator);
        return List.of();
    }

    private List<String> resolveInitiatorSelect(java.util.Map<String, Object> variables) {
        @SuppressWarnings("unchecked")
        var selected = (List<String>) variables.get("selectedAssignees");
        return selected != null ? selected : List.of();
    }

    private List<String> resolveExpression(
            String expression, java.util.Map<String, Object> variables) {
        // TODO: 对接表达式引擎
        log.debug("表达式解析审批人：expression={}", expression);
        return List.of();
    }
}
