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
 * <p><b>messageId 当前用 executionId（已知局限，待 AAF-104 #10403）</b>：AgentScope 的 replyId/blockId 尚未在
 * {@code AgentScopeEventMapper} 的 TEXT_BLOCK_START 分支进入 payload，因此无法按 block 分消息——一次执行的多个文本块
 * 会被合并成一条消息。配对跟踪已消除重复 START，前端不会出错，但"单执行多文本块"的语义要等 messageId 改为 {@code replyId:blockId} 派生后才成立。
 */
public final class TextMessageEventConverter implements AafAguiEventConverter {

    @Override
    public Set<ExecutionEventType> supportedTypes() {
        return Set.of(
                ExecutionEventType.MESSAGE_STARTED,
                ExecutionEventType.MESSAGE_DELTA,
                ExecutionEventType.MESSAGE_COMPLETED);
    }

    @Override
    public List<AguiEvent> convert(ExecutionEvent event, AafAguiStreamContext context) {
        var messageId = messageId(event);
        return switch (event.type()) {
            case MESSAGE_STARTED -> context.messageStart(messageId);
            case MESSAGE_DELTA -> context.messageContent(messageId, requiredDelta(event));
            case MESSAGE_COMPLETED -> context.messageEnd(messageId);
            default ->
                    throw new IllegalStateException(
                            "TextMessageEventConverter 收到未声明支持的类型: " + event.type());
        };
    }

    private static String messageId(ExecutionEvent event) {
        return event.executionId().value();
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
