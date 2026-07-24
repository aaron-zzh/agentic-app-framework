package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentExecutionCommand;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventPayload;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.EventId;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ExceedMaxItersEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.RequestStopEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.ToolResultState;

/** 将 AgentScope 运行事件收敛为稳定、脱敏的 AAF 事件。 */
public final class AgentScopeEventMapper {

    /** 映射单个运行事件；不对外暴露思考链和工具参数。 */
    public Optional<ExecutionEvent> map(
            AgentEvent source, AgentExecutionCommand command, MappingState state) {
        return switch (source.getType()) {
            case AGENT_START -> {
                state.status(ExecutionEventStatus.RUNNING);
                yield event(
                        source,
                        command,
                        state,
                        ExecutionEventType.RUN_STARTED,
                        ExecutionEventStatus.RUNNING,
                        payload("source", source.getSource()));
            }
            case AGENT_RESULT -> {
                var result = ((AgentResultEvent) source).getResult();
                yield event(
                        source,
                        command,
                        state,
                        ExecutionEventType.MESSAGE_COMPLETED,
                        state.status(),
                        payload(
                                "messageId",
                                result.getId(),
                                "role",
                                result.getRole().name(),
                                "text",
                                result.getTextContent()));
            }
            case AGENT_END -> mapAgentEnd((AgentEndEvent) source, command, state);
            case MODEL_CALL_START ->
                    event(
                            source,
                            command,
                            state,
                            ExecutionEventType.MODEL_CALL_STARTED,
                            state.status(),
                            ExecutionEventPayload.empty());
            case MODEL_CALL_END -> mapModelCallEnd((ModelCallEndEvent) source, command, state);
            case TEXT_BLOCK_START ->
                    event(
                            source,
                            command,
                            state,
                            ExecutionEventType.MESSAGE_STARTED,
                            state.status(),
                            ExecutionEventPayload.empty());
            case TEXT_BLOCK_DELTA -> mapTextDelta((TextBlockDeltaEvent) source, command, state);
            case TOOL_CALL_START -> mapToolStart((ToolCallStartEvent) source, command, state);
            case TOOL_RESULT_END -> mapToolResult((ToolResultEndEvent) source, command, state);
            case REQUIRE_USER_CONFIRM ->
                    mapConfirmation((RequireUserConfirmEvent) source, command, state);
            case REQUEST_STOP -> mapStop((RequestStopEvent) source, command, state);
            case EXCEED_MAX_ITERS ->
                    mapMaxIterations((ExceedMaxItersEvent) source, command, state);
            case ALL_TOOLS_DENIED -> {
                state.status(ExecutionEventStatus.FAILED);
                yield event(
                        source,
                        command,
                        state,
                        ExecutionEventType.RUN_FAILED,
                        ExecutionEventStatus.FAILED,
                        payload("reason", "ALL_TOOLS_DENIED"));
            }
            default -> Optional.empty();
        };
    }

    /** 将基础设施异常收敛为无敏感详情的运行失败事件。 */
    public ExecutionEvent failure(
            AgentExecutionCommand command, MappingState state, Throwable failure) {
        state.status(ExecutionEventStatus.FAILED);
        return syntheticEvent(
                command,
                state,
                ExecutionEventType.RUN_FAILED,
                ExecutionEventStatus.FAILED,
                payload("errorType", failure.getClass().getSimpleName()));
    }

    /** 创建按 executionId 取消的确定性终态事件。 */
    public ExecutionEvent canceled(AgentExecutionCommand command, MappingState state) {
        state.status(ExecutionEventStatus.CANCELED);
        return syntheticEvent(
                command,
                state,
                ExecutionEventType.EXECUTION_CANCELED,
                ExecutionEventStatus.CANCELED,
                ExecutionEventPayload.empty());
    }

    private Optional<ExecutionEvent> mapAgentEnd(
            AgentEndEvent source, AgentExecutionCommand command, MappingState state) {
        if (state.status() != ExecutionEventStatus.RUNNING) {
            return Optional.empty();
        }
        state.status(ExecutionEventStatus.VERIFYING);
        return event(
                source,
                command,
                state,
                ExecutionEventType.RUN_COMPLETED,
                ExecutionEventStatus.VERIFYING,
                payload("replyId", source.getReplyId()));
    }

    private Optional<ExecutionEvent> mapModelCallEnd(
            ModelCallEndEvent source, AgentExecutionCommand command, MappingState state) {
        var usage = source.getUsage();
        var values = new LinkedHashMap<String, Object>();
        if (usage != null) {
            values.put("inputTokens", usage.getInputTokens());
            values.put("outputTokens", usage.getOutputTokens());
            values.put("cachedTokens", usage.getCachedTokens());
            values.put("durationSeconds", usage.getTime());
        }
        return event(
                source,
                command,
                state,
                ExecutionEventType.MODEL_CALL_COMPLETED,
                state.status(),
                new ExecutionEventPayload(values));
    }

    private Optional<ExecutionEvent> mapTextDelta(
            TextBlockDeltaEvent source, AgentExecutionCommand command, MappingState state) {
        return event(
                source,
                command,
                state,
                ExecutionEventType.MESSAGE_DELTA,
                state.status(),
                payload("replyId", source.getReplyId(), "delta", source.getDelta()));
    }

    private Optional<ExecutionEvent> mapToolStart(
            ToolCallStartEvent source, AgentExecutionCommand command, MappingState state) {
        return event(
                source,
                command,
                state,
                ExecutionEventType.TOOL_CALL_STARTED,
                state.status(),
                payload(
                        "toolCallId",
                        source.getToolCallId(),
                        "toolName",
                        source.getToolCallName()));
    }

    private Optional<ExecutionEvent> mapToolResult(
            ToolResultEndEvent source, AgentExecutionCommand command, MappingState state) {
        var resultState = source.getState();
        var successful = resultState == ToolResultState.SUCCESS;
        return event(
                source,
                command,
                state,
                successful
                        ? ExecutionEventType.TOOL_CALL_COMPLETED
                        : ExecutionEventType.TOOL_CALL_FAILED,
                state.status(),
                payload(
                        "toolCallId",
                        source.getToolCallId(),
                        "toolName",
                        source.getToolCallName(),
                        "resultState",
                        resultState == null ? "UNKNOWN" : resultState.name()));
    }

    private Optional<ExecutionEvent> mapConfirmation(
            RequireUserConfirmEvent source,
            AgentExecutionCommand command,
            MappingState state) {
        state.status(ExecutionEventStatus.AWAITING_AUTHORIZATION);
        var toolNames = source.getToolCalls().stream().map(call -> call.getName()).toList();
        return event(
                source,
                command,
                state,
                ExecutionEventType.APPROVAL_REQUESTED,
                ExecutionEventStatus.AWAITING_AUTHORIZATION,
                payload("tools", toolNames));
    }

    private Optional<ExecutionEvent> mapStop(
            RequestStopEvent source, AgentExecutionCommand command, MappingState state) {
        if (source.getGenerateReason() == GenerateReason.PERMISSION_ASKING) {
            state.status(ExecutionEventStatus.AWAITING_AUTHORIZATION);
            return Optional.empty();
        }
        state.status(ExecutionEventStatus.PAUSED);
        return event(
                source,
                command,
                state,
                ExecutionEventType.EXECUTION_PAUSED,
                ExecutionEventStatus.PAUSED,
                payload("reason", source.getGenerateReason().name()));
    }

    private Optional<ExecutionEvent> mapMaxIterations(
            ExceedMaxItersEvent source, AgentExecutionCommand command, MappingState state) {
        state.status(ExecutionEventStatus.FAILED);
        return event(
                source,
                command,
                state,
                ExecutionEventType.RUN_FAILED,
                ExecutionEventStatus.FAILED,
                payload(
                        "reason",
                        "MAX_ITERATIONS",
                        "maxIterations",
                        source.getMaxIters(),
                        "currentIteration",
                        source.getCurrentIter()));
    }

    private Optional<ExecutionEvent> event(
            AgentEvent source,
            AgentExecutionCommand command,
            MappingState state,
            ExecutionEventType type,
            ExecutionEventStatus status,
            ExecutionEventPayload payload) {
        var rawId = source.getId();
        var eventId =
                rawId == null || rawId.isBlank()
                        ? new EventId(randomId())
                        : new EventId(rawId);
        return Optional.of(
                create(
                        eventId,
                        Instant.parse(source.getCreatedAt()),
                        command,
                        state,
                        type,
                        status,
                        payload));
    }

    private ExecutionEvent syntheticEvent(
            AgentExecutionCommand command,
            MappingState state,
            ExecutionEventType type,
            ExecutionEventStatus status,
            ExecutionEventPayload payload) {
        return create(
                new EventId(randomId()),
                Instant.now(),
                command,
                state,
                type,
                status,
                payload);
    }

    private ExecutionEvent create(
            EventId eventId,
            Instant createdAt,
            AgentExecutionCommand command,
            MappingState state,
            ExecutionEventType type,
            ExecutionEventStatus status,
            ExecutionEventPayload payload) {
        var context = command.context();
        return new ExecutionEvent(
                eventId,
                context.tenantId(),
                context.conversationId(),
                context.sessionId(),
                context.taskId(),
                context.executionId(),
                context.runId(),
                context.parentExecutionId(),
                state.nextSequence(),
                type,
                status,
                context.controlMode(),
                OwnerType.AGENT,
                context.assistantId(),
                command.agentId(),
                context.userId(),
                context.correlationId(),
                context.causationId(),
                context.idempotencyKey(),
                payload,
                createdAt);
    }

    private static ExecutionEventPayload payload(Object... pairs) {
        var values = new LinkedHashMap<String, Object>();
        for (var index = 0; index < pairs.length; index += 2) {
            var value = pairs[index + 1];
            if (value != null) {
                values.put((String) pairs[index], value);
            }
        }
        return new ExecutionEventPayload(values);
    }

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 单个运行流内的序列和生命周期快照。 */
    public static final class MappingState {
        private final AtomicLong sequence;
        private final AtomicReference<ExecutionEventStatus> status =
                new AtomicReference<>(ExecutionEventStatus.RUNNING);

        public MappingState(long sequenceBase) {
            if (sequenceBase < 0) {
                throw new IllegalArgumentException("sequenceBase 不能小于 0");
            }
            this.sequence = new AtomicLong(sequenceBase);
        }

        long nextSequence() {
            return sequence.incrementAndGet();
        }

        ExecutionEventStatus status() {
            return status.get();
        }

        void status(ExecutionEventStatus next) {
            status.set(next);
        }
    }
}
