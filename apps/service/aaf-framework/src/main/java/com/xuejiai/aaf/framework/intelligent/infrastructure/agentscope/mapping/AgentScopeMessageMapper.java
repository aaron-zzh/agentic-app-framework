package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping;

import java.util.ArrayList;
import java.util.List;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentMessage;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
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
            return reasoningMsg(message);
        }
        var role = MsgRole.valueOf(message.role().name());
        var content = new ArrayList<ContentBlock>();
        content.add(TextBlock.builder().text(message.text()).build());
        message.attachments().stream().map(this::attachmentBlock).forEach(content::add);
        return Msg.builderForRole(role)
                .id(message.messageId())
                .content(List.copyOf(content))
                .build();
    }

    /**
     * {@code REASONING} 回放为携带 {@link ThinkingBlock} 的 {@code ASSISTANT} 消息——{@code MsgRole} 没有对应的
     * REASONING 枚举值，推理块本就是模型上一轮输出内容的一部分（AAF-106 #10604）。禁止携带附件（构造器已保证 REASONING
     * 消息不允许附件，此处不重复校验）。
     */
    private Msg reasoningMsg(AgentMessage message) {
        var content = List.<ContentBlock>of(ThinkingBlock.builder().thinking(message.text()).build());
        return Msg.builderForRole(MsgRole.ASSISTANT)
                .id(message.messageId())
                .content(content)
                .build();
    }

    /** 附件统一映射为 {@link DataBlock}——官方 {@code ImageBlock}/{@code AudioBlock}/{@code VideoBlock} 仅为向后兼容保留，新代码优先用 {@code DataBlock}。 */
    private ContentBlock attachmentBlock(AgentMessage.Attachment attachment) {
        return DataBlock.builder()
                .source(
                        URLSource.builder()
                                .url(attachment.signedUrl())
                                .mimeType(attachment.mimeType())
                                .build())
                .build();
    }
}
