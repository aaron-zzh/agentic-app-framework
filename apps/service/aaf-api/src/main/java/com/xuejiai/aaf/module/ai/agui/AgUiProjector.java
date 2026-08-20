package com.xuejiai.aaf.module.ai.agui;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.publication.AafAiTaskEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.publication.ExecutionEventPublicMapper;
import com.xuejiai.aaf.module.ai.chat.agui.AgUiEvent;

/** 内部执行事件到 AG-UI 标准事件或安全 aaf.* CUSTOM 的唯一映射。 */
@Component
public final class AgUiProjector {

    private final ExecutionEventPublicMapper publicMapper;

    public AgUiProjector(ExecutionEventPublicMapper publicMapper) {
        this.publicMapper = publicMapper;
    }

    public List<AgUiEvent> project(ExecutionEvent event) {
        return switch (event.type()) {
            case EXECUTION_STARTED -> List.of(AgUiEvent.runStarted(event.runId().value()));
            case MESSAGE_STARTED ->
                    List.of(
                            AgUiEvent.textMessageStart(
                                    event.runId().value(), event.executionId().value()));
            case MESSAGE_DELTA ->
                    List.of(
                            AgUiEvent.textMessageContent(
                                    event.runId().value(),
                                    event.executionId().value(),
                                    requiredDelta(event)));
            case MESSAGE_COMPLETED ->
                    List.of(
                            AgUiEvent.textMessageEnd(
                                    event.runId().value(), event.executionId().value()));
            case EXECUTION_COMPLETED -> List.of(AgUiEvent.runFinished(event.runId().value()));
            case EXECUTION_FAILED, EXECUTION_CANCELED, COMMAND_REJECTED ->
                    List.of(AgUiEvent.runError(event.runId().value(), "Assistant 运行未完成"));
            default -> projectSafe(publicMapper.map(event));
        };
    }

    private static List<AgUiEvent> projectSafe(AafAiTaskEvent event) {
        var data = event.data().values();
        return switch (event.type()) {
            case "aaf.tool.started" ->
                    text(data, "toolCallId") == null || text(data, "toolName") == null
                            ? custom(event)
                            : List.of(
                                    AgUiEvent.toolCallStart(
                                            event.runId(),
                                            text(data, "toolCallId"),
                                            text(data, "toolName")));
            case "aaf.tool.completed", "aaf.tool.failed" ->
                    text(data, "toolCallId") == null
                            ? custom(event)
                            : List.of(
                                    AgUiEvent.toolCallResult(
                                            event.runId(), text(data, "toolCallId"), data));
            default -> custom(event);
        };
    }

    private static String requiredDelta(ExecutionEvent event) {
        var delta = event.payload().values().get("delta");
        if (delta instanceof String text) {
            return text;
        }
        throw new IllegalStateException("MESSAGE_DELTA 缺少字符串 delta");
    }

    private static List<AgUiEvent> custom(AafAiTaskEvent event) {
        return List.of(AgUiEvent.custom(event.runId(), event.type(), event));
    }

    private static String text(Map<String, Object> values, String key) {
        var value = values.get(key);
        return value instanceof String text ? text : null;
    }
}
