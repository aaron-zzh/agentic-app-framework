package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
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
import io.agentscope.core.event.AllToolsDeniedEvent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.ExceedMaxItersEvent;
import io.agentscope.core.event.ExternalExecutionResultEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.RequestStopEvent;
import io.agentscope.core.event.RequireExternalExecutionEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.TextBlockEndEvent;
import io.agentscope.core.event.TextBlockStartEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.event.UserConfirmResultEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
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

    /**
     * 映射单个运行事件。
     *
     * <p><b>穷举无 default（RQ-04 配套 / AAF-104 #10401）</b>：本 switch 覆盖 {@code AgentEventType} 全部 31 项且
     * 故意不写 {@code default}——上游任何版本新增枚举常量都会让本类**编译失败**，强制做出显式处置决定，而不是被 静默丢弃导致前端可见信息无声缺失。若运行时 jar
     * 比编译期新，未匹配常量会抛 {@code MatchException} 并被执行 适配器收敛为 RUN_FAILED，属 fail-closed，优于静默降级。
     *
     * <p>三类处置各有明确理由，改动前先读对应注释：
     *
     * <ul>
     *   <li><b>映射</b> — 有对应 AAF 业务语义，产出脱敏后的 {@code ExecutionEvent}
     *   <li><b>安全忽略</b> — 思考内容（不外发原始 CoT）、二进制块（只传引用）、core 内部提示（无业务语义）
     *   <li><b>不应出现</b> — 已关闭的官方能力（如原生 subagent），出现即记录配置漂移告警
     * </ul>
     *
     * <p>全部 31 项已落地显式映射（AAF-104 #10403 第二增量补齐最后 8 项：{@code TEXT_BLOCK_END}、{@code
     * TOOL_CALL_DELTA/END}、{@code TOOL_RESULT_START/TEXT_DELTA}、{@code USER_CONFIRM_RESULT}、{@code
     * EXTERNAL_EXECUTION_RESULT}、{@code REQUIRE_EXTERNAL_EXECUTION}）。文本与工具流式事件的 payload 统一携带 {@code
     * replyId}（及 {@code blockId}/{@code toolCallId}），供 AG-UI 投影层按官方约定聚合还原；工具调用入参与
     * 工具执行文本输出的增量正文暂不透出（只落长度），schema 脱敏留给消费方或后续投影层任务处理。
     */
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
            case TEXT_BLOCK_START -> {
                var start = (TextBlockStartEvent) source;
                yield event(
                        source,
                        command,
                        agentIdentifier,
                        state,
                        ExecutionEventType.MESSAGE_STARTED,
                        state.status(),
                        payload("replyId", start.getReplyId(), "blockId", start.getBlockId()));
            }
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
            // 全部工具被拒不是终态：core 只在某个 middleware 回应 RequestStopEvent 时才停止推理，
            // 否则默认继续下一轮（ReActAgent.java:3495-3515 的 stopRef 分支 + 官方 message-and-event 文档）。
            // AAF 只注册 PromptEnvelopeCaptureMiddleware，不处理该事件，因此 Agent 必然继续。
            // 此前映射为 RUN_FAILED + status=FAILED 造成三重错误：终态事件让 RQ-05 重放守卫误判执行已结算；
            // status 污染使 mapAgentEnd 的 RUNNING 守卫失效、RUN_COMPLETED 永不发出；AG-UI 侧 runFinished
            // 置位后该 run 所有后续事件被抑制——用户看到"失败"后永久静默，而 Agent 仍在工作且可能成功。
            // 语义正确的落点是既有的 AUTHORIZATION_DENIED（HITL 拒绝动作授权），公共事件 aaf.authorization.denied
            // 前端已消费。若产品要求"全拒即停"，需另立任务注册发 RequestStopEvent 的 middleware，让终态成真。
            case ALL_TOOLS_DENIED -> {
                var denied = ((AllToolsDeniedEvent) source).getDeniedToolCalls();
                yield event(
                        source,
                        command,
                        agentIdentifier,
                        state,
                        ExecutionEventType.AUTHORIZATION_DENIED,
                        state.status(),
                        payload(
                                "reason",
                                "ALL_TOOLS_DENIED",
                                "deniedToolCount",
                                denied == null ? 0 : denied.size(),
                                "deniedToolNames",
                                denied == null
                                        ? List.of()
                                        : denied.stream().map(ToolUseBlock::getName).toList()));
            }

            // ===== 安全忽略：思考内容不外发（AAF-106 #10603 同一约束，在本层拦截而非投影层过滤） =====
            // 原始 CoT 属披露层决策，与模型是否开启思考无关；阶段感知由 STEP/Activity 表达，不伪造成 reasoning
            case THINKING_BLOCK_START, THINKING_BLOCK_DELTA, THINKING_BLOCK_END -> Optional.empty();

            // ===== 安全忽略：二进制块不进事件账本 =====
            // 图片/音频等内容经工具证据与产物引用传递，事件里只放引用，避免把 base64 写进事件表
            case DATA_BLOCK_START, DATA_BLOCK_DELTA, DATA_BLOCK_END, TOOL_RESULT_DATA_DELTA ->
                    Optional.empty();

            // ===== 安全忽略：core 内部提示，无对应 AAF 业务语义 =====
            case HINT_BLOCK, CUSTOM -> Optional.empty();

            // ===== 映射：文本块与工具四段生命周期（AAF-104 #10403 第二增量） =====
            // TEXT_BLOCK_END 不复用 MESSAGE_COMPLETED（AGENT_RESULT 专用，表示整次回复结束），
            // 而是落 MESSAGE_BLOCK_COMPLETED——两者各自一次配对，一次回复可含多个文本块。
            case TEXT_BLOCK_END ->
                    mapTextBlockEnd((TextBlockEndEvent) source, command, agentIdentifier, state);
            case TOOL_CALL_DELTA ->
                    mapToolCallDelta((ToolCallDeltaEvent) source, command, agentIdentifier, state);
            case TOOL_CALL_END ->
                    mapToolCallEnd((ToolCallEndEvent) source, command, agentIdentifier, state);
            case TOOL_RESULT_START ->
                    mapToolResultStart(
                            (ToolResultStartEvent) source, command, agentIdentifier, state);
            case TOOL_RESULT_TEXT_DELTA ->
                    mapToolResultTextDelta(
                            (ToolResultTextDeltaEvent) source, command, agentIdentifier, state);
            case USER_CONFIRM_RESULT ->
                    mapUserConfirmResult(
                            (UserConfirmResultEvent) source, command, agentIdentifier, state);
            case REQUIRE_EXTERNAL_EXECUTION ->
                    mapRequireExternalExecution(
                            (RequireExternalExecutionEvent) source,
                            command,
                            agentIdentifier,
                            state);
            case EXTERNAL_EXECUTION_RESULT ->
                    mapExternalExecutionResult(
                            (ExternalExecutionResultEvent) source, command, agentIdentifier, state);

            // ===== 不应出现：配置漂移告警 =====
            // AAF 不启用官方 subagent（TaskBoard 是唯一外部编排），出现该事件说明工具面或 builder 配置被改动
            case SUBAGENT_EXPOSED -> {
                log.warn(
                        "[AgentLoop] 收到官方 subagent 事件，但 AAF 未启用原生子智能体，疑似配置漂移：executionId={}，source={}",
                        command.context().executionId().value(),
                        source.getSource());
                yield Optional.empty();
            }
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

    /**
     * 创建按 executionId 暂停的确定性终态事件（AAF-110）。与 {@link #canceled} 语义不同：责任主体不变， 状态槎不删除，下次同一 {@code
     * executionId} 重新发起可续接对话历史——{@code pause(ExecutionId)} 版本的终态仲裁分支专用，不与 {@code RequestStopEvent}
     * 驱动的 {@code mapStop} 共享调用路径。
     */
    public ExecutionEvent paused(
            AgentExecutionCommand command, String agentIdentifier, MappingState state) {
        state.status(ExecutionEventStatus.PAUSED);
        return syntheticEvent(
                command,
                agentIdentifier,
                state,
                ExecutionEventType.EXECUTION_PAUSED,
                ExecutionEventStatus.PAUSED,
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

    /** 文本增量：按 replyId + blockId 聚合即可还原完整回复的某个文本块（一次回复可含多个文本块）。 */
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
                payload(
                        "replyId",
                        source.getReplyId(),
                        "blockId",
                        source.getBlockId(),
                        "delta",
                        source.getDelta()));
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

    /**
     * 文本块结束：与 {@code MESSAGE_COMPLETED}（{@code AGENT_RESULT}，整次回复终态）不同，本事件是回复内某一个
     * 文本块的终态，一次回复可含多个文本块，各自一次 START/END 配对，不可合并成同一个 AAF 事件类型。
     */
    private Optional<ExecutionEvent> mapTextBlockEnd(
            TextBlockEndEvent source,
            AgentExecutionCommand command,
            String agentIdentifier,
            MappingState state) {
        return event(
                source,
                command,
                agentIdentifier,
                state,
                ExecutionEventType.MESSAGE_BLOCK_COMPLETED,
                state.status(),
                payload("replyId", source.getReplyId(), "blockId", source.getBlockId()));
    }

    /**
     * 工具调用入参流式增量：只落长度不落原始 JSON 片段。
     *
     * <p>入参可能携带模型从上下文摘取的业务敏感字段值，与 {@link #mapToolStart} 排除入参出边界的理由相同；按 schema 脱敏后再逐片透出留给 AAF-104
     * #10403 的 AG-UI 投影层决定，本层只保证身份与长度可追踪。
     */
    private Optional<ExecutionEvent> mapToolCallDelta(
            ToolCallDeltaEvent source,
            AgentExecutionCommand command,
            String agentIdentifier,
            MappingState state) {
        var delta = source.getDelta();
        return event(
                source,
                command,
                agentIdentifier,
                state,
                ExecutionEventType.TOOL_CALL_ARGS_DELTA,
                state.status(),
                payload(
                        "replyId",
                        source.getReplyId(),
                        "toolCallId",
                        source.getToolCallId(),
                        "toolName",
                        source.getToolCallName(),
                        "deltaLength",
                        delta == null ? 0 : delta.codePointCount(0, delta.length())));
    }

    /** 工具调用入参流结束：区别于 {@code TOOL_CALL_COMPLETED}（工具执行结果成功），本事件只表示入参已拼齐。 */
    private Optional<ExecutionEvent> mapToolCallEnd(
            ToolCallEndEvent source,
            AgentExecutionCommand command,
            String agentIdentifier,
            MappingState state) {
        return event(
                source,
                command,
                agentIdentifier,
                state,
                ExecutionEventType.TOOL_CALL_ARGS_COMPLETED,
                state.status(),
                payload(
                        "replyId",
                        source.getReplyId(),
                        "toolCallId",
                        source.getToolCallId(),
                        "toolName",
                        source.getToolCallName()));
    }

    /** 工具开始执行：入参已确定、结果尚未返回的中间态，供前端展示"执行中"。 */
    private Optional<ExecutionEvent> mapToolResultStart(
            ToolResultStartEvent source,
            AgentExecutionCommand command,
            String agentIdentifier,
            MappingState state) {
        return event(
                source,
                command,
                agentIdentifier,
                state,
                ExecutionEventType.TOOL_RESULT_STARTED,
                state.status(),
                payload(
                        "replyId",
                        source.getReplyId(),
                        "toolCallId",
                        source.getToolCallId(),
                        "toolName",
                        source.getToolCallName()));
    }

    /** 工具执行期间的文本输出增量：与 {@link #mapToolCallDelta} 同理，只落长度，正文经工具证据链路传递。 */
    private Optional<ExecutionEvent> mapToolResultTextDelta(
            ToolResultTextDeltaEvent source,
            AgentExecutionCommand command,
            String agentIdentifier,
            MappingState state) {
        var delta = source.getDelta();
        return event(
                source,
                command,
                agentIdentifier,
                state,
                ExecutionEventType.TOOL_RESULT_DELTA,
                state.status(),
                payload(
                        "replyId",
                        source.getReplyId(),
                        "toolCallId",
                        source.getToolCallId(),
                        "toolName",
                        source.getToolCallName(),
                        "deltaLength",
                        delta == null ? 0 : delta.codePointCount(0, delta.length())));
    }

    /**
     * 用户确认结果：{@code replyId} 与最初暂停时的 {@code REQUIRE_USER_CONFIRM} 相同，因此复用既有 {@code
     * APPROVAL_RESOLVED}（与 {@link #mapConfirmation} 的 {@code APPROVAL_REQUESTED} 配对），不新增类型。
     * 只暴露确认结果与工具名，被拒工具的修改后入参（{@code ConfirmResult.toolCall}）不出边界。
     */
    private Optional<ExecutionEvent> mapUserConfirmResult(
            UserConfirmResultEvent source,
            AgentExecutionCommand command,
            String agentIdentifier,
            MappingState state) {
        state.status(ExecutionEventStatus.RUNNING);
        var results = source.getConfirmResults();
        var toolNames =
                results.stream()
                        .map(ConfirmResult::getToolCall)
                        .filter(Objects::nonNull)
                        .map(ToolUseBlock::getName)
                        .toList();
        var confirmedCount = results.stream().filter(ConfirmResult::isConfirmed).count();
        return event(
                source,
                command,
                agentIdentifier,
                state,
                ExecutionEventType.APPROVAL_RESOLVED,
                ExecutionEventStatus.RUNNING,
                payload(
                        "replyId",
                        source.getReplyId(),
                        "tools",
                        toolNames,
                        "confirmedCount",
                        confirmedCount,
                        "totalCount",
                        (long) results.size()));
    }

    /** 需要外部（进程外）执行：与 {@code AUTHORIZATION_REQUESTED} 不同，这是执行位置转移而非权限决策。 */
    private Optional<ExecutionEvent> mapRequireExternalExecution(
            RequireExternalExecutionEvent source,
            AgentExecutionCommand command,
            String agentIdentifier,
            MappingState state) {
        state.status(ExecutionEventStatus.PAUSED);
        var toolNames = source.getToolCalls().stream().map(ToolUseBlock::getName).toList();
        return event(
                source,
                command,
                agentIdentifier,
                state,
                ExecutionEventType.EXTERNAL_EXECUTION_REQUESTED,
                ExecutionEventStatus.PAUSED,
                payload("replyId", source.getReplyId(), "tools", toolNames));
    }

    /**
     * 外部执行结果已回填：{@code replyId} 与请求时的 {@code REQUIRE_EXTERNAL_EXECUTION} 相同。只暴露工具调用标识与结果数量， {@code
     * ToolResultBlock} 的正文内容不出边界（与 {@link #mapToolResult} 对工具证据的处置一致）。
     */
    private Optional<ExecutionEvent> mapExternalExecutionResult(
            ExternalExecutionResultEvent source,
            AgentExecutionCommand command,
            String agentIdentifier,
            MappingState state) {
        state.status(ExecutionEventStatus.RUNNING);
        var toolCallIds =
                source.getToolResults().stream()
                        .map(ToolResultBlock::getId)
                        .filter(Objects::nonNull)
                        .toList();
        return event(
                source,
                command,
                agentIdentifier,
                state,
                ExecutionEventType.EXTERNAL_EXECUTION_SUPPLIED,
                ExecutionEventStatus.RUNNING,
                payload(
                        "replyId",
                        source.getReplyId(),
                        "toolCallIds",
                        toolCallIds,
                        "resultCount",
                        (long) source.getToolResults().size()));
    }

    /**
     * 权限引擎 ASK 决策：转 APPROVAL_REQUESTED，带 replyId 与每个待确认工具的 toolCallId/工具名。
     *
     * <p><b>当前生产路径不可达（AAF-104 #10404 核实确认）</b>：{@code RequireUserConfirmEvent} 只在构建 {@code
     * ReActAgent} 时配置 {@code PermissionContextState} 才会触发——全仓核实 AAF 从未配置。真正驱动 AAF 授权确认的是 {@code
     * DefaultToolGateway.invoke} → {@code AUTHORIZATION_REQUESTED}，与本方法完全 独立。本方法的 {@code replyId}
     * 补全是修正 Javadoc 声称与代码实际不一致的既有 bug，不代表该路径已被启用； 若未来 AAF 配置了 core 权限系统，这里的映射已经就位。
     */
    private Optional<ExecutionEvent> mapConfirmation(
            RequireUserConfirmEvent source,
            AgentExecutionCommand command,
            String agentIdentifier,
            MappingState state) {
        state.status(ExecutionEventStatus.AWAITING_AUTHORIZATION);
        var toolCallIds = source.getToolCalls().stream().map(call -> call.getId()).toList();
        var toolNames = source.getToolCalls().stream().map(call -> call.getName()).toList();
        return event(
                source,
                command,
                agentIdentifier,
                state,
                ExecutionEventType.APPROVAL_REQUESTED,
                ExecutionEventStatus.AWAITING_AUTHORIZATION,
                payload(
                        "replyId", source.getReplyId(),
                        "toolCallIds", toolCallIds,
                        "tools", toolNames));
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
                createdAt,
                // 节点身份随调用上下文透传：AG-UI 投影据此区分交付类节点与内部节点
                context.nodeIdentity());
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
