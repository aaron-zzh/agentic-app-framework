package com.xuejiai.aaf.module.system.workflow.approval;

import java.util.List;

/**
 * 审批节点配置。
 *
 * @param assigneeStrategy 审批人策略
 * @param assignees 固定审批人列表（FIXED_USER 时使用）
 * @param roleKey 角色标识（ROLE 时使用）
 * @param expression 表达式（EXPRESSION 时使用）
 * @param formId 审批表单 ID
 * @param requiredFields 必填字段列表
 * @param timeoutStrategy 超时处理策略
 * @param timeoutHours 超时小时数
 * @param emptyAssigneeStrategy 空审批人处理策略
 */
public record ApprovalNodeConfig(
        AssigneeStrategy assigneeStrategy,
        List<String> assignees,
        String roleKey,
        String expression,
        String formId,
        List<String> requiredFields,
        TimeoutStrategy timeoutStrategy,
        Integer timeoutHours,
        EmptyAssigneeStrategy emptyAssigneeStrategy) {

    /** 审批人策略 */
    public enum AssigneeStrategy {
        /** 固定用户 */
        FIXED_USER,
        /** 按角色 */
        ROLE,
        /** 部门主管 */
        DEPARTMENT_HEAD,
        /** 发起人自选 */
        INITIATOR_SELECT,
        /** 表达式 */
        EXPRESSION
    }

    /** 超时处理策略 */
    public enum TimeoutStrategy {
        /** 自动通过 */
        AUTO_APPROVE,
        /** 自动拒绝 */
        AUTO_REJECT,
        /** 转交 */
        TRANSFER,
        /** 提醒 */
        REMIND
    }

    /** 空审批人处理策略 */
    public enum EmptyAssigneeStrategy {
        /** 跳过该节点 */
        SKIP,
        /** 转交管理员 */
        ADMIN,
        /** 报错 */
        ERROR
    }
}
