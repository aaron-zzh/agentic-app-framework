package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping;

import java.util.List;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentMessage;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;

/**
 * 在唯一基础设施边界内映射 AAF 与 AgentScope 消息。
 *
 * <p>入向单向映射：AAF {@code AgentMessage} → AgentScope {@code Msg}。 出向不走这里——模型输出由 {@link
 * AgentScopeEventMapper} 从事件流收敛。
 */
public final class AgentScopeMessageMapper {

    /** 将纯 AAF 消息映射为 AgentScope 消息。角色枚举名一一对应，仅传文本内容。 */
    public List<Msg> toAgentScope(List<AgentMessage> messages) {
        return messages.stream().map(this::toAgentScope).toList();
    }

    private Msg toAgentScope(AgentMessage message) {
        var role = MsgRole.valueOf(message.role().name());
        return Msg.builderForRole(role).id(message.messageId()).textContent(message.text()).build();
    }
}
