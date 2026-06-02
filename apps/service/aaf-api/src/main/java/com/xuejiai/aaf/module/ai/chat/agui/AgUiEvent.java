package com.xuejiai.aaf.module.ai.chat.agui;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * AG-UI 协议事件——Spring AI 直连链路的 SSE 事件序列化模型。
 *
 * <p>用于 Spring AI {@code ResilientChatService} 流式调用链路（{@code AiChatHandler}、 {@code
 * AgUiChatController}、{@code ChatOrchestrationService} 等）将 LLM 响应转换为 AG-UI 标准事件推送给前端。
 *
 * <p>与 AgentScope AG-UI 链路（{@code /agui/runs} 端点，由 {@code agentscope-agui-spring-boot-starter}
 * 提供）并行存在，各自服务不同调用路径：
 *
 * <ul>
 *   <li>本类：Spring AI 直连链路，适用于简单对话、工作流节点、用户间聊天等场景
 *   <li>AgentScope AG-UI：Agent 认知循环链路，适用于需要 ReAct 推理和工具调用的复杂任务
 * </ul>
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
        return new AgUiEvent(
                AgUiEventType.RUN_STARTED, runId, null, null, null, null, null, null, null);
    }

    public static AgUiEvent runFinished(String runId) {
        return new AgUiEvent(
                AgUiEventType.RUN_FINISHED, runId, null, null, null, null, null, null, null);
    }

    public static AgUiEvent runError(String runId, String error) {
        return new AgUiEvent(
                AgUiEventType.RUN_ERROR, runId, null, null, null, null, null, null, error);
    }

    public static AgUiEvent textMessageStart(String runId, String messageId) {
        return new AgUiEvent(
                AgUiEventType.TEXT_MESSAGE_START,
                runId,
                messageId,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public static AgUiEvent textMessageContent(String runId, String messageId, String delta) {
        return new AgUiEvent(
                AgUiEventType.TEXT_MESSAGE_CONTENT,
                runId,
                messageId,
                delta,
                null,
                null,
                null,
                null,
                null);
    }

    public static AgUiEvent textMessageEnd(String runId, String messageId) {
        return new AgUiEvent(
                AgUiEventType.TEXT_MESSAGE_END,
                runId,
                messageId,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public static AgUiEvent toolCallStart(String runId, String toolCallId, String toolCallName) {
        return new AgUiEvent(
                AgUiEventType.TOOL_CALL_START,
                runId,
                null,
                null,
                toolCallId,
                toolCallName,
                null,
                null,
                null);
    }

    public static AgUiEvent toolCallArgs(String runId, String toolCallId, String args) {
        return new AgUiEvent(
                AgUiEventType.TOOL_CALL_ARGS,
                runId,
                null,
                null,
                toolCallId,
                null,
                args,
                null,
                null);
    }

    public static AgUiEvent toolCallEnd(String runId, String toolCallId) {
        return new AgUiEvent(
                AgUiEventType.TOOL_CALL_END, runId, null, null, toolCallId, null, null, null, null);
    }

    public static AgUiEvent toolCallResult(String runId, String toolCallId, String result) {
        return new AgUiEvent(
                AgUiEventType.TOOL_CALL_RESULT,
                runId,
                null,
                null,
                toolCallId,
                null,
                null,
                result,
                null);
    }
}
