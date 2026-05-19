package com.xuejiai.aaf.module.system.agui;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * AG-UI 协议事件。
 *
 * <p>每个事件通过 SSE data: 行发送给前端，前端 assistant-ui 组件库按 type 分发处理。
 *
 * @author AaronZZH & Kiro
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgUiEvent(
        AgUiEventType type,
        String runId,
        String messageId,
        String delta,
        String toolCallId,
        String toolCallName,
        String args,
        String result,
        String error) {

    /** AG-UI 协议事件类型 */
    public enum AgUiEventType {
        RUN_STARTED,
        RUN_FINISHED,
        RUN_ERROR,
        RUN_CANCELLED,
        TEXT_MESSAGE_START,
        TEXT_MESSAGE_CONTENT,
        TEXT_MESSAGE_END,
        TOOL_CALL_START,
        TOOL_CALL_ARGS,
        TOOL_CALL_END,
        TOOL_CALL_RESULT,
        STATE_SNAPSHOT,
        STATE_DELTA,
        MESSAGES_SNAPSHOT
    }

    // ========== 工厂方法 ==========

    public static AgUiEvent runStarted(String runId) {
        return new AgUiEvent(AgUiEventType.RUN_STARTED, runId, null, null, null, null, null, null, null);
    }

    public static AgUiEvent runFinished(String runId) {
        return new AgUiEvent(AgUiEventType.RUN_FINISHED, runId, null, null, null, null, null, null, null);
    }

    public static AgUiEvent runError(String runId, String error) {
        return new AgUiEvent(AgUiEventType.RUN_ERROR, runId, null, null, null, null, null, null, error);
    }

    public static AgUiEvent textMessageStart(String runId, String messageId) {
        return new AgUiEvent(AgUiEventType.TEXT_MESSAGE_START, runId, messageId, null, null, null, null, null, null);
    }

    public static AgUiEvent textMessageContent(String runId, String messageId, String delta) {
        return new AgUiEvent(AgUiEventType.TEXT_MESSAGE_CONTENT, runId, messageId, delta, null, null, null, null, null);
    }

    public static AgUiEvent textMessageEnd(String runId, String messageId) {
        return new AgUiEvent(AgUiEventType.TEXT_MESSAGE_END, runId, messageId, null, null, null, null, null, null);
    }

    public static AgUiEvent toolCallStart(String runId, String toolCallId, String toolCallName) {
        return new AgUiEvent(AgUiEventType.TOOL_CALL_START, runId, null, null, toolCallId, toolCallName, null, null, null);
    }

    public static AgUiEvent toolCallArgs(String runId, String toolCallId, String args) {
        return new AgUiEvent(AgUiEventType.TOOL_CALL_ARGS, runId, null, null, toolCallId, null, args, null, null);
    }

    public static AgUiEvent toolCallEnd(String runId, String toolCallId) {
        return new AgUiEvent(AgUiEventType.TOOL_CALL_END, runId, null, null, toolCallId, null, null, null, null);
    }

    public static AgUiEvent toolCallResult(String runId, String toolCallId, String result) {
        return new AgUiEvent(AgUiEventType.TOOL_CALL_RESULT, runId, null, null, toolCallId, null, null, result, null);
    }
}
