package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentExecutionCommand;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelSpec;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.tool.ToolResultEvidenceStore;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventPayload;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AgentId;
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
import lombok.extern.slf4j.Slf4j;

/**
 * 将 AgentScope 运行事件收敛为稳定、脱敏的 AAF 事件。
 *
 * <p>AgentScope 侧有 20+ 类型化事件，AAF 只保留对外契约需要的子集；思考链、工具入参、 原始错误信息不出边界。工具业务证据从 {@link
 * ToolResultEvidenceStore} 取回后按白名单合并。
 */
@Slf4j
public final class AgentScopeEventMapper {

    private final ToolResultEvidenceStore evidenceStore;

    public AgentScopeEventMapper(ToolResultEvidenceStore evidenceStore) {
        this.evidenceStore = evidenceStore;
    }

    /** 映射单个运行事件；不对外暴露思考链和工具参数。未识别类型返回 empty 直接丢弃。 */
    public Optional<ExecutionEvent> map(
            AgentEvent source,
            AgentExecutionCommand command,
            String agentIdentifier,
            ModelSpec model,
            MappingState state) {
        return switch (source.getType()) {
            case AGENT_START -> {
                state.status(ExecutionEventStatus.RUNNING);
                yield event(
                        source,
                        command,
                        agentIdentifier,
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
                        agentIdentifier,
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
            case AGENT_END -> mapAgentEnd((AgentEndEvent) source, command, agentIdentifier, state);
            case MODEL_CALL_START ->
                    event(
                            source,
                            command,
                            agentIdentifier,
                            state,
                            ExecutionEventType.MODEL_CALL_STARTED,
                            state.status(),
                            ExecutionEventPayload.empty());
            case MODEL_CALL_END ->
                    mapModelCallEnd(
                            (ModelCallEndEvent) source, command, agentIdentifier, model, state);
            case TEXT_BLOCK_START ->
                    event(
                            source,
                            command,
                            agentIdentifier,
                            state,
                            ExecutionEventType.MESSAGE_STARTED,
                            state.status(),
                            ExecutionEventPayload.empty());
            case TEXT_BLOCK_DELTA ->
                    mapTextDelta((TextBlockDeltaEvent) source, command, agentIdentifier, state);
            case TOOL_CALL_START ->
                    mapToolStart((ToolCallStartEvent) source, command, agentIdentifier, state);
            case TOOL_RESULT_END ->
                    mapToolResult((ToolResultEndEvent) source, command, agentIdentifier, state);
            case REQUIRE_USER_CONFIRM ->
                    mapConfirmation(
                            (RequireUserConfirmEvent) source, command, agentIdentifier, state);
            case REQUEST_STOP ->
                    mapStop((RequestStopEvent) source, command, agentIdentifier, state);
            case EXCEED_MAX_ITERS ->
                    mapMaxIterations((ExceedMaxItersEvent) source, command, agentIdentifier, state);
            case ALL_TOOLS_DENIED -> {
                state.status(ExecutionEventStatus.FAILED);
                yield event(
                        source,
                        command,
                        agentIdentifier,
                        state,
                        ExecutionEventType.RUN_FAILED,
                        ExecutionEventStatus.FAILED,
                        payload("reason", "ALL_TOOLS_DENIED"));
            }
            default -> Optional.empty();
        };
    }

    /**
     * 将执行期异常收敛为无敏感详情的运行失败事件。
     *
     * <p>对外只给稳定契约字段：{@code failureCategory}（处置类别）、{@code retryable}（是否可自动重试）、 {@code
     * failureId}（服务端日志关联键）。异常原文与堆栈只写服务端日志，调用方不得再靠异常类简单名 判断可重试性（RQ-04）。
     */
    public ExecutionEvent failure(
            AgentExecutionCommand command,
            String agentIdentifier,
            MappingState state,
            Throwable failure) {
        state.status(ExecutionEventStatus.FAILED);
        var category = AgentScopeFailureClassifier.classify(failure);
        var failureId = randomId();
        log.error(
                "[AgentLoop] 执行失败：failureId={}，executionId={}，agent={}，失败类别={}，可重试={}",
                failureId,
                command.context().executionId().value(),
                agentIdentifier,
                category,
                category.retryable(),
                failure);
        return syntheticEvent(
                command,
                agentIdentifier,
                state,
                ExecutionEventType.RUN_FAILED,
                ExecutionEventStatus.FAILED,
                payload(
                        "failureCategory",
                        category.name(),
                        "retryable",
                        category.retryable(),
                        "failureId",
                        failureId,
                        "errorType",
                        failure == null ? "UNKNOWN" : failure.getClass().getSimpleName()));
    }

    /** 创建按 executionId 取消的确定性终态事件。 */
    public ExecutionEvent canceled(
            AgentExecutionCommand command, String agentIdentifier, MappingState state) {
        state.status(ExecutionEventStatus.CANCELED);
        return syntheticEvent(
                command,
                agentIdentifier,
                state,
                ExecutionEventType.EXECUTION_CANCELED,
                ExecutionEventStatus.CANCELED,
                ExecutionEventPayload.empty());
    }

    /** AGENT_END → RUN_COMPLETED；已进入终态（失败/取消/待授权）时不再覆盖。 */
    private Optional<ExecutionEvent> mapAgentEnd(
            AgentEndEvent source,
            AgentExecutionCommand command,
            String agentIdentifier,
            MappingState state) {
        if (state.status() != ExecutionEventStatus.RUNNING) {
            return Optional.empty();
        }
        state.status(ExecutionEventStatus.VERIFYING);
        return event(
                source,
                command,
                agentIdentifier,
                state,
                ExecutionEventType.RUN_COMPLETED,
                ExecutionEventStatus.VERIFYING,
                payload("replyId", source.getReplyId()));
    }

    /** 模型调用结束：带上 token 用量快照供前端展示；usage 缺失时只保留模型标识。 */
    private Optional<ExecutionEvent> mapModelCallEnd(
            ModelCallEndEvent source,
            AgentExecutionCommand command,
            String agentIdentifier,
            ModelSpec model,
            MappingState state) {
        var usage = source.getUsage();
        var values = new LinkedHashMap<String, Object>();
        values.put("modelId", model.modelId());
        values.put("capability", "CHAT");
        if (usage != null) {
            values.put("inputTokens", usage.getInputTokens());
            values.put("outputTokens", usage.getOutputTokens());
            values.put("cachedTokens", usage.getCachedTokens());
            values.put("durationSeconds", usage.getTime());
        }
        return event(
                source,
                command,
                agentIdentifier,
                state,
                ExecutionEventType.MODEL_CALL_COMPLETED,
                state.status(),
                new ExecutionEventPayload(values));
    }

    /** 文本增量：按 replyId 聚合即可还原完整回复。 */
    private Optional<ExecutionEvent> mapTextDelta(
            TextBlockDeltaEvent source,
            AgentExecutionCommand command,
            String agentIdentifier,
            MappingState state) {
        return event(
                source,
                command,
                agentIdentifier,
                state,
                ExecutionEventType.MESSAGE_DELTA,
                state.status(),
                payload("replyId", source.getReplyId(), "delta", source.getDelta()));
    }

    /** 工具开始：只暴露 toolCallId 与工具名，模型生成的入参不出边界。 */
    private Optional<ExecutionEvent> mapToolStart(
            ToolCallStartEvent source,
            AgentExecutionCommand command,
            String agentIdentifier,
            MappingState state) {
        return event(
                source,
                command,
                agentIdentifier,
                state,
                ExecutionEventType.TOOL_CALL_STARTED,
                state.status(),
                payload(
                        "toolCallId",
                        source.getToolCallId(),
                        "toolName",
                        source.getToolCallName()));
    }

    /** 工具结束：证据标记需授权 → AUTHORIZATION_REQUESTED，否则按成功/失败分流。 */
    private Optional<ExecutionEvent> mapToolResult(
            ToolResultEndEvent source,
            AgentExecutionCommand command,
            String agentIdentifier,
            MappingState state) {
        var resultState = source.getState();
        var successful = resultState == ToolResultState.SUCCESS;
        var evidence =
                evidenceStore
                        .take(command.context().executionId(), source.getToolCallId())
                        .orElseGet(java.util.Map::of);
        if (!successful && Boolean.TRUE.equals(evidence.get("authorizationRequired"))) {
            state.status(ExecutionEventStatus.AWAITING_AUTHORIZATION);
            var values = toolResultPayload(source, resultState, evidence);
            return event(
                    source,
                    command,
                    agentIdentifier,
                    state,
                    ExecutionEventType.AUTHORIZATION_REQUESTED,
                    ExecutionEventStatus.AWAITING_AUTHORIZATION,
                    new ExecutionEventPayload(values));
        }
        return event(
                source,
                command,
                agentIdentifier,
                state,
                successful
                        ? ExecutionEventType.TOOL_CALL_COMPLETED
                        : ExecutionEventType.TOOL_CALL_FAILED,
                state.status(),
                new ExecutionEventPayload(toolResultPayload(source, resultState, evidence)));
    }

    /** 工具结果载荷：标识 + 结果状态 + 已过滤的业务证据。 */
    private static LinkedHashMap<String, Object> toolResultPayload(
            ToolResultEndEvent source,
            ToolResultState resultState,
            java.util.Map<String, Object> evidence) {
        var values = new LinkedHashMap<String, Object>();
        values.put("toolCallId", source.getToolCallId());
        values.put("toolName", source.getToolCallName());
        values.put("resultState", resultState == null ? "UNKNOWN" : resultState.name());
        values.putAll(evidence);
        return values;
    }

    /** 权限引擎 ASK 决策：转 APPROVAL_REQUESTED，只带待确认工具名。 */
    private Optional<ExecutionEvent> mapConfirmation(
            RequireUserConfirmEvent source,
            AgentExecutionCommand command,
            String agentIdentifier,
            MappingState state) {
        state.status(ExecutionEventStatus.AWAITING_AUTHORIZATION);
        var toolNames = source.getToolCalls().stream().map(call -> call.getName()).toList();
        return event(
                source,
                command,
                agentIdentifier,
                state,
                ExecutionEventType.APPROVAL_REQUESTED,
                ExecutionEventStatus.AWAITING_AUTHORIZATION,
                payload("tools", toolNames));
    }

    /** 停止请求：权限等待与工具挂起只改状态不发事件（由授权事件表达），其余为 EXECUTION_PAUSED。 */
    private Optional<ExecutionEvent> mapStop(
            RequestStopEvent source,
            AgentExecutionCommand command,
            String agentIdentifier,
            MappingState state) {
        if (source.getGenerateReason() == GenerateReason.PERMISSION_ASKING
                || source.getGenerateReason() == GenerateReason.TOOL_SUSPENDED) {
            state.status(ExecutionEventStatus.AWAITING_AUTHORIZATION);
            return Optional.empty();
        }
        state.status(ExecutionEventStatus.PAUSED);
        return event(
                source,
                command,
                agentIdentifier,
                state,
                ExecutionEventType.EXECUTION_PAUSED,
                ExecutionEventStatus.PAUSED,
                payload("reason", source.getGenerateReason().name()));
    }

    /** 超出最大迭代：视为运行失败，附迭代上限便于排查。 */
    private Optional<ExecutionEvent> mapMaxIterations(
            ExceedMaxItersEvent source,
            AgentExecutionCommand command,
            String agentIdentifier,
            MappingState state) {
        state.status(ExecutionEventStatus.FAILED);
        return event(
                source,
                command,
                agentIdentifier,
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

    /** 由源事件构造 AAF 事件：沿用源事件 id 与时间戳，id 缺失时兜底随机。 */
    private Optional<ExecutionEvent> event(
            AgentEvent source,
            AgentExecutionCommand command,
            String agentIdentifier,
            MappingState state,
            ExecutionEventType type,
            ExecutionEventStatus status,
            ExecutionEventPayload payload) {
        var rawId = source.getId();
        var eventId =
                rawId == null || rawId.isBlank() ? new EventId(randomId()) : new EventId(rawId);
        return Optional.of(
                create(
                        eventId,
                        Instant.parse(source.getCreatedAt()),
                        command,
                        agentIdentifier,
                        state,
                        type,
                        status,
                        payload));
    }

    /** 无源事件的合成事件（失败 / 取消），id 与时间戳本地生成。 */
    private ExecutionEvent syntheticEvent(
            AgentExecutionCommand command,
            String agentIdentifier,
            MappingState state,
            ExecutionEventType type,
            ExecutionEventStatus status,
            ExecutionEventPayload payload) {
        return create(
                new EventId(randomId()),
                Instant.now(),
                command,
                agentIdentifier,
                state,
                type,
                status,
                payload);
    }

    /** 统一填充调用上下文标识（租户 / 会话 / 任务 / 追踪链）。 */
    private ExecutionEvent create(
            EventId eventId,
            Instant createdAt,
            AgentExecutionCommand command,
            String agentIdentifier,
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
                state.nextProvisionalSequence(),
                type,
                status,
                context.controlMode(),
                OwnerType.AGENT,
                context.assistantId(),
                new AgentId(agentIdentifier),
                context.userId(),
                context.correlationId(),
                context.causationId(),
                context.idempotencyKey(),
                payload,
                createdAt);
    }

    /** 按 key-value 交替入参构造载荷，null 值直接跳过。 */
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

    /** 单个运行流内的序列和生命周期快照。逐事件推进状态机，供后续事件复用当前状态。 */
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

        /** 仅满足入库前事件契约；持久 sequence 由 ExecutionEventStorePort 数据库原子重分配。 */
        long nextProvisionalSequence() {
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
