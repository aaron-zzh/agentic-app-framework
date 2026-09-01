package com.xuejiai.aaf.framework.intelligent.shared.event;

/** AAF 执行轨迹中可跨层观察的事实类型。 */
public enum ExecutionEventType {
    EXECUTION_STARTED,
    EXECUTION_COMPLETED,
    EXECUTION_FAILED,
    EXECUTION_CANCELED,
    EXECUTION_PAUSED,
    EXECUTION_RESUMED,
    COMMAND_REJECTED,

    RUN_STARTED,
    RUN_COMPLETED,
    RUN_FAILED,

    MESSAGE_STARTED,
    MESSAGE_DELTA,
    MESSAGE_COMPLETED,
    /**
     * 单次回复内某个文本块结束（AgentScope {@code TEXT_BLOCK_END}）。区别于 {@code MESSAGE_COMPLETED}（整次回复结束，来自
     * {@code AGENT_RESULT}）：ReAct 一次回复可能产出多个文本块，两者各自一次配对，不可合并成同一个 AG-UI 消息终态事件。
     */
    MESSAGE_BLOCK_COMPLETED,

    MODEL_CALL_STARTED,
    MODEL_CALL_COMPLETED,
    MODEL_CALL_FAILED,

    TOOL_CALL_STARTED,
    /** 工具调用入参流式增量（AgentScope {@code TOOL_CALL_DELTA}），落地前需按 schema 脱敏。 */
    TOOL_CALL_ARGS_DELTA,
    /** 工具调用入参流结束（AgentScope {@code TOOL_CALL_END}）。区别于 {@code TOOL_CALL_COMPLETED}（工具执行结果成功）。 */
    TOOL_CALL_ARGS_COMPLETED,
    TOOL_CALL_COMPLETED,
    TOOL_CALL_FAILED,
    /** 工具开始执行（AgentScope {@code TOOL_RESULT_START}），发生在入参完成之后、结果返回之前。 */
    TOOL_RESULT_STARTED,
    /** 工具执行期间的文本输出增量（AgentScope {@code TOOL_RESULT_TEXT_DELTA}）。 */
    TOOL_RESULT_DELTA,

    AUTHORIZATION_REQUESTED,
    AUTHORIZATION_GRANTED,
    AUTHORIZATION_DENIED,
    AUTHORIZATION_REVOKED,

    APPROVAL_REQUESTED,
    APPROVAL_RESOLVED,

    /**
     * 工具调用需要外部（进程外）执行（AgentScope {@code REQUIRE_EXTERNAL_EXECUTION}）。与 {@code
     * AUTHORIZATION_REQUESTED} 不同：后者是权限引擎的人工确认，前者是执行位置转移（如需要客户端本地环境完成）， 不涉及授权决策。
     */
    EXTERNAL_EXECUTION_REQUESTED,
    /** 外部执行结果已回填（AgentScope {@code EXTERNAL_EXECUTION_RESULT}），replyId 与请求时相同用于配对。 */
    EXTERNAL_EXECUTION_SUPPLIED,

    CLARIFICATION_REQUESTED,
    CLARIFICATION_UPDATED,
    CLARIFICATION_RESOLVED,
    CLARIFICATION_CANCELED,
    CLARIFICATION_EXPIRED,

    ITERATION_EVALUATED,
    ITERATION_STOPPED,

    SUBTASK_CREATED,
    SUBTASK_STARTED,
    SUBTASK_COMPLETED,
    SUBTASK_FAILED,
    SUBTASK_CANCELED,

    /**
     * EXECUTOR 计划生命周期事件（AAF-107 #10705）。计划级 6 个，对齐 {@code SUBTASK_*} 的"终态一律拆成独立枚举值"模式，
     * 不用 outcome 字段合并。{@code REVIEW_REQUIRED}/{@code REJECTED} 转换在当前架构下是死代码路径（ADR-006
     * 固定自动批准，人工审批分支从未被触发），因此不设 {@code EXECUTOR_PLAN_APPROVED}/{@code EXECUTOR_PLAN_REJECTED}
     * 独立事件——{@code SUBMITTED} 与"批准"在当前实现里永远同时发生，拆分没有独立观察价值。
     */
    EXECUTOR_PLAN_CREATED,
    /** {@code submit}：DRAFT/PLANNING → SUBMITTED（随即自动转 APPROVED，见上方说明）。 */
    EXECUTOR_PLAN_SUBMITTED,
    /** {@code claimApproved}：APPROVED → EXECUTING，冻结 revision 供执行阶段引用。 */
    EXECUTOR_PLAN_EXECUTION_STARTED,
    EXECUTOR_PLAN_COMPLETED,
    EXECUTOR_PLAN_FAILED,
    /** {@code cancel}：委托任务级联取消（{@code stop}/{@code takeOver}）时随同取消尚未终结的计划。 */
    EXECUTOR_PLAN_CANCELLED,

    /**
     * EXECUTOR 计划步骤级事件（AAF-107 #10705）。由新增的 {@code report_executor_step} 工具驱动——模型在
     * {@code executeApprovedPlanSteps} 阶段显式上报步骤边界（复用 {@code submit_executor_plan}
     * 已确立的"模型主动上报"模式，而非从底层工具调用事件流反推，后者无法可靠映射 0~N 次工具调用到一个 step 边界）。
     */
    EXECUTOR_PLAN_STEP_STARTED,
    EXECUTOR_PLAN_STEP_COMPLETED,
    EXECUTOR_PLAN_STEP_FAILED,

    VALIDATION_STARTED,
    VALIDATION_COMPLETED,
    VALIDATION_FAILED,

    RECOVERY_STARTED,
    RECOVERY_COMPLETED,
    OWNERSHIP_TRANSFERRED,

    TASK_STATUS_CHANGED,
    CONTROL_MODE_CHANGED,
    /** 本次执行冻结的 Role 已解析；同一任务内前后 roleKey 不同即为角色接力。 */
    ROLE_RESOLVED,

    INPUT_CANCELED,
    INPUT_MODIFIED,
    INPUT_SUPPLEMENTED,
    INPUT_UNRELATED
}
