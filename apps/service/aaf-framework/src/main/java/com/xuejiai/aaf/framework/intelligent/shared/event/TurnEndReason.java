package com.xuejiai.aaf.framework.intelligent.shared.event;

/**
 * 一个回合为什么停止。
 *
 * <p>与 {@code CompletionDecision.Outcome} 是两个正交维度：Outcome 表达业务完成判定，本枚举表达停止原因。同一个 {@code FAILED}
 * 判定可能来自上下文超限、工具被拒或模型错误，恢复策略完全不同，因此必须显式记录。
 *
 * <p>写入终态事件 payload 的 {@code turnEndReason} 键，不进入 {@link ExecutionEvent} 的记录字段——那是全部事件类型共用的契约。
 */
public enum TurnEndReason {
    /** 正常交付回复，未声明额外业务完成条件。 */
    USER_TURN_END,
    /** 显式业务完成条件已满足。 */
    BUSINESS_COMPLETED,
    /** 判定需要修复，已安排下一轮迭代。 */
    REPAIR_SCHEDULED,
    /** 移交给其他责任主体。 */
    HANDOFF,
    /** 等待用户澄清。 */
    CLARIFICATION_NEEDED,
    /** 等待用户授权。 */
    AUTHORIZATION_NEEDED,
    /** 触达迭代上限。 */
    MAX_ITERATIONS,
    /** 触达任务分解预算。 */
    DECOMPOSITION_BUDGET_EXCEEDED,
    /** 触达上下文预算。 */
    CONTEXT_EXCEEDED,
    /** 工具授权被拒或工具不可用。 */
    TOOL_DENIED,
    /** 模型调用失败。 */
    MODEL_ERROR,
    /** 会话租约失效或被抢占。 */
    LEASE_LOST,
    /** 用户主动取消。 */
    CANCELED_BY_USER,
    /** 其余未归类的内部错误。 */
    INTERNAL_ERROR
}
