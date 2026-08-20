package com.xuejiai.aaf.module.ai.chat.agui;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AG-UI 协议 SSE 事件——序列化为 {@code {"type": ..., "runId": ..., ...}}。
 *
 * <p>事件类型与字段定义见 {@code docs/reference/api/ag-ui-protocol.md}。
 *
 * @author AaronZZH
 */
public record AgUiEvent(String type, Map<String, Object> fields) {

    /** 展平为 Map 供序列化，避免嵌套 fields 节点。 */
    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("type", type);
        map.putAll(fields);
        return map;
    }

    public static AgUiEvent runStarted(String runId) {
        return of("RUN_STARTED", runId);
    }

    public static AgUiEvent runFinished(String runId) {
        return of("RUN_FINISHED", runId);
    }

    public static AgUiEvent runError(String runId, String message) {
        var event = of("RUN_ERROR", runId);
        event.fields.put("message", message);
        return event;
    }

    public static AgUiEvent textMessageStart(String runId, String messageId) {
        var event = of("TEXT_MESSAGE_START", runId);
        event.fields.put("messageId", messageId);
        return event;
    }

    public static AgUiEvent textMessageContent(String runId, String messageId, String delta) {
        var event = of("TEXT_MESSAGE_CONTENT", runId);
        event.fields.put("messageId", messageId);
        event.fields.put("delta", delta);
        return event;
    }

    public static AgUiEvent textMessageEnd(String runId, String messageId) {
        var event = of("TEXT_MESSAGE_END", runId);
        event.fields.put("messageId", messageId);
        return event;
    }

    public static AgUiEvent toolCallStart(String runId, String toolCallId, String toolName) {
        var event = of("TOOL_CALL_START", runId);
        event.fields.put("toolCallId", toolCallId);
        event.fields.put("toolName", toolName);
        return event;
    }

    public static AgUiEvent toolCallArgs(String runId, String toolCallId, String delta) {
        var event = of("TOOL_CALL_ARGS", runId);
        event.fields.put("toolCallId", toolCallId);
        event.fields.put("delta", delta);
        return event;
    }

    public static AgUiEvent toolCallResult(String runId, String toolCallId, Object result) {
        var event = of("TOOL_CALL_RESULT", runId);
        event.fields.put("toolCallId", toolCallId);
        event.fields.put("result", result);
        return event;
    }

    public static AgUiEvent custom(String runId, String name, Object value) {
        if (name == null || !name.startsWith("aaf.")) {
            throw new IllegalArgumentException("AG-UI CUSTOM name 必须使用 aaf.* 命名空间");
        }
        var event = of("CUSTOM", runId);
        event.fields.put("name", name);
        event.fields.put("value", value);
        return event;
    }

    private static AgUiEvent of(String type, String runId) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("runId", runId);
        return new AgUiEvent(type, fields);
    }
}
