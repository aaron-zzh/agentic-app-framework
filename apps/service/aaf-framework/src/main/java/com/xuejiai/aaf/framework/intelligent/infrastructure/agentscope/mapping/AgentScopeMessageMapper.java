package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping;

import java.util.ArrayList;
import java.util.List;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentMessage;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.URLSource;

/**
 * 在唯一基础设施边界内映射 AAF 与 AgentScope 消息。
 *
 * <p>入向单向映射：AAF {@code AgentMessage} → AgentScope {@code Msg}。 出向不走这里——模型输出由 {@link
 * AgentScopeEventMapper} 从事件流收敛。
 */
public final class AgentScopeMessageMapper {

    /** 将纯 AAF 消息映射为 AgentScope 消息；USER 可携带图片，SYSTEM 始终只有文本。 */
    public List<Msg> toAgentScope(List<AgentMessage> messages) {
        return messages.stream().map(this::toAgentScope).toList();
    }

    private Msg toAgentScope(AgentMessage message) {
        if (message.role() == AgentMessage.Role.REASONING) {
            throw new IllegalArgumentException("推理块回放尚未接入 AgentScope，禁止映射 REASONING 消息");
        }
        var role = MsgRole.valueOf(message.role().name());
        var content = new ArrayList<ContentBlock>();
        content.add(TextBlock.builder().text(message.text()).build());
        message.attachments().stream().map(this::imageBlock).forEach(content::add);
        return Msg.builderForRole(role)
                .id(message.messageId())
                .content(List.copyOf(content))
                .build();
    }

    private ImageBlock imageBlock(AgentMessage.Attachment attachment) {
        return ImageBlock.builder()
                .source(
                        URLSource.builder()
                                .url(attachment.signedUrl())
                                .mimeType(attachment.mimeType())
                                .build())
                .build();
    }
}
