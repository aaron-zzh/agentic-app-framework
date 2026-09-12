package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping;

import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;

import io.agentscope.core.agent.RuntimeContext;

/**
 * 将显式 AAF 调用上下文映射为 per-call AgentScope 上下文。
 *
 * <p>ReActAgent 实例被多租户共享，隔离完全依赖每次调用传入的 RuntimeContext。
 */
public final class AgentScopeRuntimeContextMapper {

    /** extra 键：租户标识。 */
    public static final String TENANT_ID_KEY = "aaf.tenantId";

    /** extra 键：真实用户标识。 */
    public static final String USER_ID_KEY = "aaf.userId";

    /** extra 键：状态隔离键，与 RuntimeContext.userId 同值。 */
    public static final String STATE_USER_KEY = "aaf.stateUserKey";

    /** AgentScope userId 使用不可歧义的 tenant namespace；完整上下文和身份键显式注入。 */
    public RuntimeContext toAgentScope(InvocationContext context, String agentIdentifier) {
        Objects.requireNonNull(context, "context 不能为空");
        var stateUserKey = stateUserKey(context, agentIdentifier);
        return RuntimeContext.builder()
                .userId(stateUserKey)
                .sessionId(context.sessionId().value())
                .put(TENANT_ID_KEY, context.tenantId().value())
                .put(USER_ID_KEY, context.userId().value())
                .put(STATE_USER_KEY, stateUserKey)
                // 工具执行期需要按 typed key 取回原始上下文（见 PortBackedAgentTool）
                .put(InvocationContext.class, context)
                .build();
    }

    /**
     * 返回 AgentStateStore 使用的稳定隔离键：租户 / 用户 / 任务 / Agent 身份 / 状态槽。
     *
     * <p>{@code stateSlotId} 表示同一 attempt 的持久状态身份，不能混入 Dispatch generation/fence；后者只在状态存取边界校验当前执行权。
     * fresh attempt 使用新状态槽，same-attempt resume 则在 Dispatch 换代后继续命中原状态槽。
     */
    public String stateUserKey(InvocationContext context, String agentIdentifier) {
        Objects.requireNonNull(context, "context 不能为空");
        if (agentIdentifier == null || agentIdentifier.isBlank()) {
            throw new IllegalArgumentException("agentIdentifier 不能为空白");
        }
        var executionScope =
                context.taskId() == null
                        ? "direct=" + context.executionId().value()
                        : "task=" + context.taskId().value();
        return "tenant="
                + context.tenantId().value()
                + "|user="
                + context.userId().value()
                + '|'
                + executionScope
                + "|agent="
                + agentIdentifier
                + "|stateSlot="
                + context.stateSlotId();
    }
}
