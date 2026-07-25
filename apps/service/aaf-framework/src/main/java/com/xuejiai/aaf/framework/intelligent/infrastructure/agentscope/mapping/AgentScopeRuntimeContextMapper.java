package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping;

import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import io.agentscope.core.agent.RuntimeContext;

/** 将显式 AAF 调用上下文映射为 per-call AgentScope 上下文。 */
public final class AgentScopeRuntimeContextMapper {

    public static final String TENANT_ID_KEY = "aaf.tenantId";
    public static final String USER_ID_KEY = "aaf.userId";
    public static final String STATE_USER_KEY = "aaf.stateUserKey";

    /**
     * AgentScope userId 使用不可歧义的 tenant namespace；完整上下文和身份键显式注入。
     */
    public RuntimeContext toAgentScope(InvocationContext context) {
        Objects.requireNonNull(context, "context 不能为空");
        var stateUserKey = stateUserKey(context);
        return RuntimeContext.builder()
                .userId(stateUserKey)
                .sessionId(context.sessionId().value())
                .put(TENANT_ID_KEY, context.tenantId().value())
                .put(USER_ID_KEY, context.userId().value())
                .put(STATE_USER_KEY, stateUserKey)
                .put(InvocationContext.class, context)
                .build();
    }

    /** 返回 AgentStateStore 使用的 tenant/user/task 隔离键；委托态按 fencing 代际隔离。 */
    public String stateUserKey(InvocationContext context) {
        Objects.requireNonNull(context, "context 不能为空");
        var key = "tenant=" + context.tenantId().value()
                + "|user=" + context.userId().value()
                + "|task=" + context.taskId().value();
        if (context.controlMode()
                == com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode.DELEGATED) {
            key += "|fence=" + context.lease().fencingToken();
        }
        return key;
    }
}
