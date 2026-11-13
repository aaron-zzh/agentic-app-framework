package com.xuejiai.aaf.module.ai.agui.converter;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.module.ai.agui.AafAguiEventConverter;
import com.xuejiai.aaf.module.ai.agui.AafAguiStreamContext;

import io.agentscope.core.agui.event.AguiEvent;

/**
 * 交付节点思考过程投影为 AG-UI {@code REASONING_*}。
 *
 * <p>只有交付节点会走到这里：内部节点（执行者、已委派的协调者）由 registry 降级到 {@link InternalNodeEventConverter}，
 * 后者对思考事件直接丢弃，不外发。开关关闭时本 converter 不产出任何事件，思考内容不出服务端。
 *
 * <p>messageId 用 {@code replyId:blockId:reasoning} 派生，与同一块的文本消息（{@code replyId:blockId}）区分命名空间。
 */
public final class ReasoningMessageEventConverter implements AafAguiEventConverter {

    private final boolean enabled;

    public ReasoningMessageEventConverter(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public Set<ExecutionEventType> supportedTypes() {
        return Set.of(
                ExecutionEventType.REASONING_STARTED,
                ExecutionEventType.REASONING_DELTA,
                ExecutionEventType.REASONING_COMPLETED);
    }

    @Override
    public List<AguiEvent> convert(ExecutionEvent event, AafAguiStreamContext context) {
        if (!enabled) {
            return List.of();
        }
        return switch (event.type()) {
            // 思考块延迟到首个非空增量才开启：已落库的 START/END 在任务续流回补时不带增量，提前开启会产生空思考块
            case REASONING_STARTED -> List.of();
            case REASONING_DELTA -> {
                // 与文本增量同一约束：AG-UI 要求 delta 非空，空片段直接丢弃
                var delta = event.payload().values().get("delta");
                yield delta instanceof String text && !text.isEmpty()
                        ? context.reasoningContent(reasoningMessageId(event), text)
                        : List.of();
            }
            case REASONING_COMPLETED -> context.reasoningEnd(reasoningMessageId(event));
            default ->
                    throw new IllegalStateException(
                            "ReasoningMessageEventConverter 收到未声明支持的类型: " + event.type());
        };
    }

    private static String reasoningMessageId(ExecutionEvent event) {
        var values = event.payload().values();
        return requiredText(values, "replyId")
                + ":"
                + requiredText(values, "blockId")
                + ":reasoning";
    }

    private static String requiredText(Map<String, Object> values, String key) {
        if (values.get(key) instanceof String text) {
            return text;
        }
        throw new IllegalStateException("思考事件缺少字符串 " + key);
    }
}
