package com.xuejiai.aaf.module.ai.agui.converter;

import java.util.List;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.module.ai.agui.AafAguiEventConverter;
import com.xuejiai.aaf.module.ai.agui.AafAguiStreamContext;

import io.agentscope.core.agui.event.AguiEvent;

/**
 * 助手文本消息三段式投影。
 *
 * <p><b>messageId 用 {@code replyId:blockId} 派生（AAF-104 #10403）</b>：AgentScope 一次回复（{@code replyId}）
 * 可能产出多个文本块（{@code blockId}），每个块在 AG-UI 协议里是独立的一条消息（各自 START/CONTENT/END）。 {@code
 * MESSAGE_STARTED}/{@code MESSAGE_DELTA} 均带 {@code replyId}+{@code blockId}（见 {@code
 * AgentScopeEventMapper} 的 {@code TEXT_BLOCK_START}/{@code TEXT_BLOCK_DELTA} 分支），因此可以精确派生。
 *
 * <p><b>{@code MESSAGE_BLOCK_COMPLETED} 才是块级收尾</b>（对应 AgentScope {@code TEXT_BLOCK_END}），本
 * converter 一并声明支持它并触发对应 {@code TextMessageEnd}——区别于 {@code MESSAGE_COMPLETED}（对应 {@code
 * AGENT_RESULT}， 整次回复结束，不携带 {@code blockId}）。{@code MESSAGE_COMPLETED} 不再触发块级 END：一次回复的最后一个块理应已经收到
 * 独立的 {@code MESSAGE_BLOCK_COMPLETED}；若因异常提前结束导致某个块未收到显式 END，由 {@link AafAguiStreamContext#close()}
 * 的流尾兜底逻辑补齐，不在此处重复处理。
 */
public final class TextMessageEventConverter implements AafAguiEventConverter {

    @Override
    public Set<ExecutionEventType> supportedTypes() {
        return Set.of(
                ExecutionEventType.MESSAGE_STARTED,
                ExecutionEventType.MESSAGE_DELTA,
                ExecutionEventType.MESSAGE_BLOCK_COMPLETED,
                ExecutionEventType.MESSAGE_COMPLETED);
    }

    @Override
    public List<AguiEvent> convert(ExecutionEvent event, AafAguiStreamContext context) {
        return switch (event.type()) {
            case MESSAGE_STARTED -> context.messageStart(blockMessageId(event));
            case MESSAGE_DELTA ->
                    context.messageContent(blockMessageId(event), requiredDelta(event));
            case MESSAGE_BLOCK_COMPLETED -> context.messageEnd(blockMessageId(event));
            // 整次回复结束（AGENT_RESULT）不对应具体 blockId，块级 END 已由 MESSAGE_BLOCK_COMPLETED 负责；
            // 未闭合的边界情况交给流尾 close() 兜底，此处不重复发送。
            case MESSAGE_COMPLETED -> List.of();
            default ->
                    throw new IllegalStateException(
                            "TextMessageEventConverter 收到未声明支持的类型: " + event.type());
        };
    }

    /** {@code replyId:blockId} 派生，确保同一次回复的不同文本块映射为不同 AG-UI 消息。 */
    private static String blockMessageId(ExecutionEvent event) {
        var values = event.payload().values();
        var replyId = requiredText(values, "replyId");
        var blockId = requiredText(values, "blockId");
        return replyId + ":" + blockId;
    }

    private static String requiredText(java.util.Map<String, Object> values, String key) {
        var value = values.get(key);
        if (value instanceof String text) {
            return text;
        }
        throw new IllegalStateException("消息事件缺少字符串 " + key);
    }

    /** delta 缺失是映射层缺陷而非可容忍的空事件，fail-fast 让问题停在这里而不是发出畸形 AG-UI 事件。 */
    private static String requiredDelta(ExecutionEvent event) {
        var delta = event.payload().values().get("delta");
        if (delta instanceof String text) {
            return text;
        }
        throw new IllegalStateException("MESSAGE_DELTA 缺少字符串 delta");
    }
}
