package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.agent.model.ActivatedSkill;
import com.xuejiai.aaf.framework.intelligent.agent.model.AgentExecutionCommand;
import com.xuejiai.aaf.framework.intelligent.agent.model.AgentMessage;
import com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizedSkillSummary;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt.PromptLayerKind;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt.PromptLayerSource;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt.PromptSourceKind;
import com.xuejiai.aaf.framework.intelligent.agent.model.ExecutionPolicy;
import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ModelSelectionRequirement;
import com.xuejiai.aaf.framework.intelligent.agent.model.SkillExecutionProfile;
import com.xuejiai.aaf.framework.intelligent.agent.model.SkillSelectionManifest;
import com.xuejiai.aaf.framework.intelligent.agent.model.SubagentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillActivationMode;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillScope;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillSelectionMode;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort.Lease;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelSpec;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillVersionRef;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeEventMapper.MappingState;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.tool.ToolResultEvidenceStore;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CausationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.IdempotencyKey;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.AllToolsDeniedEvent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.CustomEvent;
import io.agentscope.core.event.DataBlockDeltaEvent;
import io.agentscope.core.event.DataBlockEndEvent;
import io.agentscope.core.event.DataBlockStartEvent;
import io.agentscope.core.event.ExceedMaxItersEvent;
import io.agentscope.core.event.ExternalExecutionResultEvent;
import io.agentscope.core.event.HintBlockEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.ModelCallStartEvent;
import io.agentscope.core.event.RequestStopEvent;
import io.agentscope.core.event.RequireExternalExecutionEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.SubagentExposedEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.TextBlockEndEvent;
import io.agentscope.core.event.TextBlockStartEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockEndEvent;
import io.agentscope.core.event.ThinkingBlockStartEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultDataDeltaEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.event.UserConfirmResultEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;

/**
 * {@link AgentScopeEventMapper#map} 穷举 switch 的正常映射路径断言（AAF-108 #10802 补齐门禁恢复阶段欠下的测试）。
 *
 * <p>此前只有 {@link AgentScopeEventMapperFailureTest}（专测 {@code failure(...)}）、{@link
 * AgentScopeFailureClassifierTest}、{@link AgentScopeRuntimeContextMapperTest} 三个文件，均不覆盖 {@code
 * map(...)} 本体——AAF-104 落地时"阶段约束不新增测试文件"曾要求断言补进既有文件，但三者职责均与"31 项事件正常映射"无关，塞入会破坏单一职责， 按 AAF-105
 * 已确立的处理模式（发现例外时新建并如实记录偏离）新建本文件。
 */
class AgentScopeEventMapperTest {

    private final AgentScopeEventMapper mapper =
            new AgentScopeEventMapper(new ToolResultEvidenceStore());

    // ===== 映射：核心生命周期 =====

    @Test
    @DisplayName("Given AGENT_START When 映射 Then 产出 RUN_STARTED 且状态转 RUNNING")
    void should_map_agent_start_to_run_started() {
        var state = new MappingState(0);

        var event =
                mapper.map(
                        new AgentStartEvent("session-1", "reply-1", "agent-name"),
                        command(),
                        "agent.mapper-test",
                        model(),
                        state);

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type()).isEqualTo(ExecutionEventType.RUN_STARTED);
        assertThat(event.orElseThrow().status()).isEqualTo(ExecutionEventStatus.RUNNING);
        // AAF 是平级编排（ADR-005 议题二），不使用 core 的嵌套委派模型；顶层节点的 AgentEvent.getSource()
        // 恒为 null（core 语义：null 表示事件来自顶层 Agent，非 null 时是斜杠分隔的子 Agent 转发路径），
        // payload() 遇 null 值直接跳过该键，因此顶层 AGENT_START 事件的 payload 不应包含 source 键。
        // 构造参数中的 "agent-name" 对应 AgentStartEvent.getName()（Agent 自身标识），与 getSource()
        // （父子转发路径）是两个完全不同的字段，不应混淆断言。
        assertThat(event.orElseThrow().payload().values()).doesNotContainKey("source");
        assertThat(state.status()).isEqualTo(ExecutionEventStatus.RUNNING);
    }

    @Test
    @DisplayName("Given AGENT_RESULT When 映射 Then 产出 MESSAGE_COMPLETED 且携带正文")
    void should_map_agent_result_to_message_completed() {
        var result =
                Msg.builder()
                        .id("reply-1")
                        .role(MsgRole.ASSISTANT)
                        .content(TextBlock.builder().text("已完成").build())
                        .build();

        var event =
                mapper.map(
                        new AgentResultEvent(result),
                        command(),
                        "agent.mapper-test",
                        model(),
                        new MappingState(0));

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type()).isEqualTo(ExecutionEventType.MESSAGE_COMPLETED);
        assertThat(event.orElseThrow().payload().values())
                .containsEntry("messageId", "reply-1")
                .containsEntry("role", "ASSISTANT")
                .containsEntry("text", "已完成");
    }

    @Test
    @DisplayName("Given AGENT_END 且状态为 RUNNING When 映射 Then 产出 RUN_COMPLETED 且转 VERIFYING")
    void should_map_agent_end_to_run_completed_when_running() {
        var state = new MappingState(0);
        state.status(ExecutionEventStatus.RUNNING);

        var event =
                mapper.map(
                        new AgentEndEvent("reply-1"),
                        command(),
                        "agent.mapper-test",
                        model(),
                        state);

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type()).isEqualTo(ExecutionEventType.RUN_COMPLETED);
        assertThat(state.status()).isEqualTo(ExecutionEventStatus.VERIFYING);
    }

    @Test
    @DisplayName("Given AGENT_END 但已进入终态 When 映射 Then 不再覆盖终态")
    void should_ignore_agent_end_when_already_terminal() {
        var state = new MappingState(0);
        state.status(ExecutionEventStatus.FAILED);

        var event =
                mapper.map(
                        new AgentEndEvent("reply-1"),
                        command(),
                        "agent.mapper-test",
                        model(),
                        state);

        assertThat(event).isEmpty();
        assertThat(state.status()).isEqualTo(ExecutionEventStatus.FAILED);
    }

    // ===== 映射：模型调用 =====

    @Test
    @DisplayName("Given MODEL_CALL_START When 映射 Then 产出 MODEL_CALL_STARTED")
    void should_map_model_call_start() {
        var event =
                mapper.map(
                        new ModelCallStartEvent("reply-1"),
                        command(),
                        "agent.mapper-test",
                        model(),
                        new MappingState(0));

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type()).isEqualTo(ExecutionEventType.MODEL_CALL_STARTED);
    }

    @Test
    @DisplayName("Given MODEL_CALL_END 带 usage When 映射 Then 携带 token 用量")
    void should_map_model_call_end_with_usage() {
        var usage = new io.agentscope.core.model.ChatUsage(120, 45, 1.5);

        var event =
                mapper.map(
                        new ModelCallEndEvent("reply-1", usage),
                        command(),
                        "agent.mapper-test",
                        model(),
                        new MappingState(0));

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type()).isEqualTo(ExecutionEventType.MODEL_CALL_COMPLETED);
        assertThat(event.orElseThrow().payload().values())
                .containsEntry("inputTokens", 120)
                .containsEntry("outputTokens", 45)
                .containsEntry("cachedTokens", 0);
    }

    @Test
    @DisplayName("Given MODEL_CALL_END 无 usage When 映射 Then 只保留模型标识")
    void should_map_model_call_end_without_usage() {
        var event =
                mapper.map(
                        new ModelCallEndEvent("reply-1", null),
                        command(),
                        "agent.mapper-test",
                        model(),
                        new MappingState(0));

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().payload().values())
                .containsEntry("modelId", "model-1")
                .doesNotContainKey("inputTokens");
    }

    // ===== 映射：文本块生命周期 =====

    @Test
    @DisplayName("Given TEXT_BLOCK_START When 映射 Then 携带 replyId 与 blockId")
    void should_map_text_block_start() {
        var event =
                mapper.map(
                        new TextBlockStartEvent("reply-1", "block-1"),
                        command(),
                        "agent.mapper-test",
                        model(),
                        new MappingState(0));

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type()).isEqualTo(ExecutionEventType.MESSAGE_STARTED);
        assertThat(event.orElseThrow().payload().values())
                .containsEntry("replyId", "reply-1")
                .containsEntry("blockId", "block-1");
    }

    @Test
    @DisplayName("Given TEXT_BLOCK_DELTA When 映射 Then 携带增量正文")
    void should_map_text_block_delta() {
        var event =
                mapper.map(
                        new TextBlockDeltaEvent("reply-1", "block-1", "增量文本"),
                        command(),
                        "agent.mapper-test",
                        model(),
                        new MappingState(0));

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type()).isEqualTo(ExecutionEventType.MESSAGE_DELTA);
        assertThat(event.orElseThrow().payload().values()).containsEntry("delta", "增量文本");
    }

    @Test
    @DisplayName(
            "Given TEXT_BLOCK_END When 映射 Then 产出 MESSAGE_BLOCK_COMPLETED 而非 MESSAGE_COMPLETED")
    void should_map_text_block_end_to_block_completed() {
        var event =
                mapper.map(
                        new TextBlockEndEvent("reply-1", "block-1"),
                        command(),
                        "agent.mapper-test",
                        model(),
                        new MappingState(0));

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type())
                .isEqualTo(ExecutionEventType.MESSAGE_BLOCK_COMPLETED);
    }

    // ===== 映射：工具调用四段生命周期 =====

    @Test
    @DisplayName("Given TOOL_CALL_START When 映射 Then 只暴露标识不含入参")
    void should_map_tool_call_start_without_arguments() {
        var event =
                mapper.map(
                        new ToolCallStartEvent("reply-1", "call-1", "search"),
                        command(),
                        "agent.mapper-test",
                        model(),
                        new MappingState(0));

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type()).isEqualTo(ExecutionEventType.TOOL_CALL_STARTED);
        assertThat(event.orElseThrow().payload().values())
                .containsEntry("toolCallId", "call-1")
                .containsEntry("toolName", "search");
    }

    @Test
    @DisplayName("Given TOOL_CALL_DELTA When 映射 Then 只落长度不落原始入参片段")
    void should_map_tool_call_delta_length_only() {
        var event =
                mapper.map(
                        new ToolCallDeltaEvent("reply-1", "call-1", "search", "{\"q\":\"x\"}"),
                        command(),
                        "agent.mapper-test",
                        model(),
                        new MappingState(0));

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type()).isEqualTo(ExecutionEventType.TOOL_CALL_ARGS_DELTA);
        assertThat(event.orElseThrow().payload().values())
                .containsEntry("deltaLength", 9)
                .doesNotContainValue("{\"q\":\"x\"}");
    }

    @Test
    @DisplayName("Given TOOL_CALL_END When 映射 Then 产出 TOOL_CALL_ARGS_COMPLETED")
    void should_map_tool_call_end_to_args_completed() {
        var event =
                mapper.map(
                        new ToolCallEndEvent("reply-1", "call-1", "search"),
                        command(),
                        "agent.mapper-test",
                        model(),
                        new MappingState(0));

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type())
                .isEqualTo(ExecutionEventType.TOOL_CALL_ARGS_COMPLETED);
    }

    @Test
    @DisplayName("Given TOOL_RESULT_START When 映射 Then 产出中间态 TOOL_RESULT_STARTED")
    void should_map_tool_result_start() {
        var event =
                mapper.map(
                        new ToolResultStartEvent("reply-1", "call-1", "search"),
                        command(),
                        "agent.mapper-test",
                        model(),
                        new MappingState(0));

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type()).isEqualTo(ExecutionEventType.TOOL_RESULT_STARTED);
    }

    @Test
    @DisplayName("Given TOOL_RESULT_TEXT_DELTA When 映射 Then 只落长度")
    void should_map_tool_result_text_delta_length_only() {
        var event =
                mapper.map(
                        new ToolResultTextDeltaEvent("reply-1", "call-1", "search", "结果片段"),
                        command(),
                        "agent.mapper-test",
                        model(),
                        new MappingState(0));

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type()).isEqualTo(ExecutionEventType.TOOL_RESULT_DELTA);
        assertThat(event.orElseThrow().payload().values())
                .containsEntry("deltaLength", 4)
                .doesNotContainValue("结果片段");
    }

    @Test
    @DisplayName("Given TOOL_RESULT_END 成功且无待授权证据 When 映射 Then 产出 TOOL_CALL_COMPLETED")
    void should_map_tool_result_end_success_to_completed() {
        var event =
                mapper.map(
                        new ToolResultEndEvent(
                                "reply-1", "call-1", "search", ToolResultState.SUCCESS),
                        command(),
                        "agent.mapper-test",
                        model(),
                        new MappingState(0));

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type()).isEqualTo(ExecutionEventType.TOOL_CALL_COMPLETED);
    }

    @Test
    @DisplayName("Given TOOL_RESULT_END 失败且无待授权证据 When 映射 Then 产出 TOOL_CALL_FAILED")
    void should_map_tool_result_end_failure_to_failed() {
        var event =
                mapper.map(
                        new ToolResultEndEvent(
                                "reply-1", "call-1", "search", ToolResultState.ERROR),
                        command(),
                        "agent.mapper-test",
                        model(),
                        new MappingState(0));

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type()).isEqualTo(ExecutionEventType.TOOL_CALL_FAILED);
    }

    @Test
    @DisplayName("Given TOOL_RESULT_END 失败且证据标记需授权 When 映射 Then 转 AUTHORIZATION_REQUESTED")
    void should_map_tool_result_end_to_authorization_requested_when_evidence_requires_it() {
        var evidenceStore = new ToolResultEvidenceStore();
        var executionId = command().context().executionId();
        evidenceStore.record(executionId, "call-1", Map.of(), false, true);
        var mapperWithEvidence = new AgentScopeEventMapper(evidenceStore);

        var event =
                mapperWithEvidence.map(
                        new ToolResultEndEvent(
                                "reply-1", "call-1", "search", ToolResultState.ERROR),
                        command(),
                        "agent.mapper-test",
                        model(),
                        new MappingState(0));

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type())
                .isEqualTo(ExecutionEventType.AUTHORIZATION_REQUESTED);
        assertThat(event.orElseThrow().status())
                .isEqualTo(ExecutionEventStatus.AWAITING_AUTHORIZATION);
    }

    // ===== 映射：权限与外部执行 =====

    @Test
    @DisplayName("Given REQUIRE_USER_CONFIRM When 映射 Then 产出 APPROVAL_REQUESTED 且只带工具名")
    void should_map_require_user_confirm_to_approval_requested() {
        var toolCall = ToolUseBlock.builder().id("call-1").name("delete_file").build();

        var event =
                mapper.map(
                        new RequireUserConfirmEvent("reply-1", List.of(toolCall)),
                        command(),
                        "agent.mapper-test",
                        model(),
                        new MappingState(0));

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type()).isEqualTo(ExecutionEventType.APPROVAL_REQUESTED);
        assertThat(event.orElseThrow().payload().values())
                .containsEntry("tools", List.of("delete_file"));
    }

    @Test
    @DisplayName("Given USER_CONFIRM_RESULT When 映射 Then 复用 APPROVAL_RESOLVED 且不暴露修改后入参")
    void should_map_user_confirm_result_to_approval_resolved() {
        var toolCall = ToolUseBlock.builder().id("call-1").name("delete_file").build();
        var confirmResult = new ConfirmResult(true, toolCall);

        var event =
                mapper.map(
                        new UserConfirmResultEvent("reply-1", List.of(confirmResult)),
                        command(),
                        "agent.mapper-test",
                        model(),
                        new MappingState(0));

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type()).isEqualTo(ExecutionEventType.APPROVAL_RESOLVED);
        assertThat(event.orElseThrow().payload().values())
                .containsEntry("confirmedCount", 1L)
                .containsEntry("totalCount", 1L);
    }

    @Test
    @DisplayName("Given ALL_TOOLS_DENIED When 映射 Then 落 AUTHORIZATION_DENIED 而非终态")
    void should_map_all_tools_denied_to_authorization_denied_not_terminal() {
        var toolCall = ToolUseBlock.builder().id("call-1").name("delete_file").build();
        var state = new MappingState(0);
        state.status(ExecutionEventStatus.RUNNING);

        var event =
                mapper.map(
                        new AllToolsDeniedEvent(List.of(toolCall)),
                        command(),
                        "agent.mapper-test",
                        model(),
                        state);

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type()).isEqualTo(ExecutionEventType.AUTHORIZATION_DENIED);
        // 关键不变量：全拒不改变 status，后续 AGENT_END 仍能正常收敛为 RUN_COMPLETED（见注释历史教训）
        assertThat(state.status()).isEqualTo(ExecutionEventStatus.RUNNING);
    }

    @Test
    @DisplayName(
            "Given REQUIRE_EXTERNAL_EXECUTION When 映射 Then 转 PAUSED 且产出 EXTERNAL_EXECUTION_REQUESTED")
    void should_map_require_external_execution() {
        var toolCall = ToolUseBlock.builder().id("call-1").name("local_shell").build();

        var event =
                mapper.map(
                        new RequireExternalExecutionEvent("reply-1", List.of(toolCall)),
                        command(),
                        "agent.mapper-test",
                        model(),
                        new MappingState(0));

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type())
                .isEqualTo(ExecutionEventType.EXTERNAL_EXECUTION_REQUESTED);
        assertThat(event.orElseThrow().status()).isEqualTo(ExecutionEventStatus.PAUSED);
    }

    @Test
    @DisplayName("Given EXTERNAL_EXECUTION_RESULT When 映射 Then 只暴露标识与结果数量")
    void should_map_external_execution_result_without_body() {
        var toolResult =
                ToolResultBlock.builder()
                        .id("call-1")
                        .name("local_shell")
                        .output(TextBlock.builder().text("敏感输出").build())
                        .build();

        var event =
                mapper.map(
                        new ExternalExecutionResultEvent("reply-1", List.of(toolResult)),
                        command(),
                        "agent.mapper-test",
                        model(),
                        new MappingState(0));

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type())
                .isEqualTo(ExecutionEventType.EXTERNAL_EXECUTION_SUPPLIED);
        assertThat(event.orElseThrow().payload().values())
                .containsEntry("toolCallIds", List.of("call-1"))
                .containsEntry("resultCount", 1L)
                .doesNotContainValue("敏感输出");
    }

    // ===== 映射：停止与迭代上限 =====

    @Test
    @DisplayName("Given REQUEST_STOP 权限等待原因 When 映射 Then 不发事件只改状态")
    void should_map_request_stop_permission_asking_without_event() {
        var state = new MappingState(0);

        var event =
                mapper.map(
                        new RequestStopEvent(
                                "等待授权",
                                io.agentscope.core.message.GenerateReason.PERMISSION_ASKING),
                        command(),
                        "agent.mapper-test",
                        model(),
                        state);

        assertThat(event).isEmpty();
        assertThat(state.status()).isEqualTo(ExecutionEventStatus.AWAITING_AUTHORIZATION);
    }

    @Test
    @DisplayName("Given REQUEST_STOP 普通原因 When 映射 Then 产出 EXECUTION_PAUSED")
    void should_map_request_stop_to_execution_paused() {
        var event =
                mapper.map(
                        new RequestStopEvent("用户主动暂停"),
                        command(),
                        "agent.mapper-test",
                        model(),
                        new MappingState(0));

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type()).isEqualTo(ExecutionEventType.EXECUTION_PAUSED);
    }

    @Test
    @DisplayName("Given EXCEED_MAX_ITERS When 映射 Then 产出 RUN_FAILED 且携带迭代上限")
    void should_map_exceed_max_iters_to_run_failed() {
        var event =
                mapper.map(
                        new ExceedMaxItersEvent("reply-1", 10, 10),
                        command(),
                        "agent.mapper-test",
                        model(),
                        new MappingState(0));

        assertThat(event).isPresent();
        assertThat(event.orElseThrow().type()).isEqualTo(ExecutionEventType.RUN_FAILED);
        assertThat(event.orElseThrow().payload().values())
                .containsEntry("maxIterations", 10)
                .containsEntry("currentIteration", 10);
    }

    // ===== 安全忽略：思考内容、二进制块、core 内部提示 =====

    @Test
    @DisplayName("Given THINKING_BLOCK_START/DELTA/END When 映射 Then 全部忽略不产出事件")
    void should_ignore_thinking_block_events() {
        assertThat(
                        mapper.map(
                                new ThinkingBlockStartEvent("reply-1", "block-1"),
                                command(),
                                "agent.mapper-test",
                                model(),
                                new MappingState(0)))
                .isEmpty();
        assertThat(
                        mapper.map(
                                new ThinkingBlockDeltaEvent("reply-1", "block-1", "思考中"),
                                command(),
                                "agent.mapper-test",
                                model(),
                                new MappingState(0)))
                .isEmpty();
        assertThat(
                        mapper.map(
                                new ThinkingBlockEndEvent("reply-1", "block-1"),
                                command(),
                                "agent.mapper-test",
                                model(),
                                new MappingState(0)))
                .isEmpty();
    }

    @Test
    @DisplayName("Given DATA_BLOCK_START/DELTA/END 与 TOOL_RESULT_DATA_DELTA When 映射 Then 全部忽略")
    void should_ignore_binary_block_events() {
        assertThat(
                        mapper.map(
                                new DataBlockStartEvent("reply-1", "block-1"),
                                command(),
                                "agent.mapper-test",
                                model(),
                                new MappingState(0)))
                .isEmpty();
        assertThat(
                        mapper.map(
                                new DataBlockDeltaEvent("reply-1", "block-1", "base64片段"),
                                command(),
                                "agent.mapper-test",
                                model(),
                                new MappingState(0)))
                .isEmpty();
        assertThat(
                        mapper.map(
                                new DataBlockEndEvent("reply-1", "block-1"),
                                command(),
                                "agent.mapper-test",
                                model(),
                                new MappingState(0)))
                .isEmpty();
        assertThat(
                        mapper.map(
                                new ToolResultDataDeltaEvent(
                                        "reply-1",
                                        "call-1",
                                        "search",
                                        TextBlock.builder().text("data片段").build()),
                                command(),
                                "agent.mapper-test",
                                model(),
                                new MappingState(0)))
                .isEmpty();
    }

    @Test
    @DisplayName("Given HINT_BLOCK 与 CUSTOM When 映射 Then 全部忽略")
    void should_ignore_hint_and_custom_events() {
        assertThat(
                        mapper.map(
                                new HintBlockEvent("reply-1", "block-1", "core", "提示"),
                                command(),
                                "agent.mapper-test",
                                model(),
                                new MappingState(0)))
                .isEmpty();
        assertThat(
                        mapper.map(
                                new CustomEvent("custom.metric"),
                                command(),
                                "agent.mapper-test",
                                model(),
                                new MappingState(0)))
                .isEmpty();
    }

    // ===== 不应出现：配置漂移告警 =====

    @Test
    @DisplayName("Given SUBAGENT_EXPOSED（AAF 未启用原生子智能体） When 映射 Then 忽略但记录告警")
    void should_ignore_subagent_exposed_as_configuration_drift() {
        var event =
                mapper.map(
                        new SubagentExposedEvent("subagent-1", "agent-1", "session-1", "label"),
                        command(),
                        "agent.mapper-test",
                        model(),
                        new MappingState(0));

        assertThat(event).isEmpty();
    }

    // ===== 合成事件：canceled / paused =====

    @Test
    @DisplayName("Given cancel 胜出 When 构造终态事件 Then 产出 EXECUTION_CANCELED 且状态 CANCELED")
    void should_build_canceled_event() {
        var event = mapper.canceled(command(), "agent.mapper-test", new MappingState(0));

        assertThat(event.type()).isEqualTo(ExecutionEventType.EXECUTION_CANCELED);
        assertThat(event.status()).isEqualTo(ExecutionEventStatus.CANCELED);
    }

    @Test
    @DisplayName("Given pause 胜出（AAF-110） When 构造终态事件 Then 产出 EXECUTION_PAUSED 且状态 PAUSED")
    void should_build_paused_event() {
        var event = mapper.paused(command(), "agent.mapper-test", new MappingState(0));

        assertThat(event.type()).isEqualTo(ExecutionEventType.EXECUTION_PAUSED);
        assertThat(event.status()).isEqualTo(ExecutionEventStatus.PAUSED);
    }

    private static ModelSpec model() {
        return new ModelSpec("model-1");
    }

    private static AgentExecutionCommand command() {
        var dynamic =
                new SubagentSpec.Dynamic(
                        "agent.mapper-test",
                        "验证事件映射契约",
                        "只执行测试任务",
                        List.of(),
                        ExecutionPolicy.withDefaultTimeouts(
                                3, 1, java.time.Duration.ofSeconds(30), 128000),
                        ModelSelectionRequirement.balanced(),
                        false);
        return new AgentExecutionCommand(
                dynamic,
                Optional.empty(),
                AgentExecutionCommand.ExecutionMode.DIRECT,
                Optional.empty(),
                skillExecutionProfile(),
                compiled(dynamic.identifier()),
                0,
                List.of(new AgentMessage("message-1", AgentMessage.Role.USER, "执行")),
                context());
    }

    private static SkillExecutionProfile skillExecutionProfile() {
        var selection =
                new SkillSelectionManifest(
                        "test.route",
                        "test.skill",
                        SkillSelectionMode.FIXED,
                        1,
                        List.of(
                                new AuthorizedSkillSummary(
                                        "test.skill",
                                        "测试技能",
                                        "验证事件映射契约",
                                        SkillScope.SYSTEM,
                                        SkillActivationMode.ON_DEMAND,
                                        List.of(),
                                        Set.of(),
                                        Set.of())));
        return new SkillExecutionProfile(
                selection,
                List.of(
                        new ActivatedSkill(
                                "test.skill",
                                new SkillVersionRef(1L, 1L, 1),
                                SkillScope.SYSTEM,
                                SkillActivationMode.ON_DEMAND,
                                "技能提示",
                                Set.of(),
                                Set.of(),
                                List.of(),
                                List.of(),
                                false)),
                List.of());
    }

    private static CompiledSystemPrompt compiled(String identity) {
        return CompiledSystemPrompt.compile(
                List.of(
                        new PromptLayerSource(
                                PromptLayerKind.CONSTITUTION,
                                PromptSourceKind.ENGINE_TEMPLATE,
                                CompiledSystemPrompt.CONSTITUTION_NAME,
                                "1",
                                "测试 Constitution"),
                        new PromptLayerSource(
                                PromptLayerKind.IDENTITY,
                                PromptSourceKind.AAF_POLICY,
                                identity,
                                "1",
                                "测试执行身份"),
                        new PromptLayerSource(
                                PromptLayerKind.INVOCATION_POLICY,
                                PromptSourceKind.AAF_POLICY,
                                "invocation:test",
                                "1",
                                "测试调用策略"),
                        new PromptLayerSource(
                                PromptLayerKind.PERSONA,
                                PromptSourceKind.ASSISTANT_PERSONA,
                                "persona:test",
                                "1",
                                "测试 Persona")));
    }

    private static InvocationContext context() {
        return new InvocationContext(
                new TenantId("tenant-1"),
                new UserId("user-1"),
                null,
                new AssistantId("assistant-1"),
                new ConversationId("conversation-1"),
                new SessionId("session-1"),
                new TaskId("task-1"),
                new ExecutionId("execution-1"),
                new RunId("run-1"),
                null,
                new CorrelationId("correlation-1"),
                new CausationId("causation-1"),
                new IdempotencyKey("idempotency-1"),
                ControlMode.READ_ONLY,
                null,
                (Lease) null,
                new ToolAuthorizationContext(Map.of()));
    }
}
