package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.middleware;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext;
import com.xuejiai.aaf.framework.intelligent.assistant.port.PromptEnvelopePort;
import com.xuejiai.aaf.framework.intelligent.core.prompt.PromptEnvelope;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeRuntimeContextMapper;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.IdempotencyKey;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolSchema;
import reactor.core.publisher.Flux;

/**
 * provider 发送前必须冻结一份信封，内容相同的重发识别为传输重试。
 *
 * <p>{@code onModelCall} 是 AAF 在 ReAct 循环外唯一能拿到真实请求内容的切点；缺少 typed {@code InvocationContext}
 * 时必须拒绝发送，不允许静默漏记。
 */
class PromptEnvelopeCaptureMiddlewareTest extends BaseMockitoUnitTest {

    private static final TenantId TENANT_ID = new TenantId("1");
    private static final TaskId TASK_ID = new TaskId("task-1");
    private static final ExecutionId EXECUTION_ID = new ExecutionId("execution-1");

    @Mock private PromptEnvelopePort envelopes;

    @Test
    @DisplayName("Given 首次模型调用 When 发送前 Then 冻结 INITIAL 信封并继续发送")
    void should_freeze_initial_envelope_before_send() {
        // 准备参数
        var middleware = middleware();
        when(envelopes.findLatest(TENANT_ID, EXECUTION_ID)).thenReturn(Optional.empty());
        when(envelopes.append(any())).thenAnswer(answerWithSeq(1));

        // 调用
        var events =
                middleware
                        .onModelCall(null, runtimeContext(), input(), ignored -> Flux.empty())
                        .collectList()
                        .block();

        // 断言
        assertThat(events).isEmpty();
        var frozen = capturedDraft();
        assertThat(frozen.trigger()).isEqualTo(PromptEnvelope.Trigger.INITIAL);
        assertThat(frozen.attemptNo()).isEqualTo(1);
        assertThat(frozen.messages()).hasSize(1);
        assertThat(frozen.tools())
                .extracting(PromptEnvelope.ToolSchemaSnapshot::name)
                .containsExactly("knowledge.search");
        assertThat(frozen.promptSha256()).hasSize(64);
    }

    @Test
    @DisplayName("Given 与上一份信封内容相同 When 再次发送 Then 标记 RETRY 并推进 attemptNo")
    void should_mark_retry_when_request_content_repeats() {
        // 准备参数
        var middleware = middleware();
        when(envelopes.append(any())).thenAnswer(answerWithSeq(1));
        when(envelopes.findLatest(TENANT_ID, EXECUTION_ID)).thenReturn(Optional.empty());
        middleware
                .onModelCall(null, runtimeContext(), input(), ignored -> Flux.empty())
                .collectList()
                .block();
        var initial = capturedDraft().withSeq(1);
        when(envelopes.findLatest(TENANT_ID, EXECUTION_ID)).thenReturn(Optional.of(initial));

        // 调用
        middleware
                .onModelCall(null, runtimeContext(), input(), ignored -> Flux.empty())
                .collectList()
                .block();

        // 断言
        var retry = capturedDraft();
        assertThat(retry.trigger()).isEqualTo(PromptEnvelope.Trigger.RETRY);
        assertThat(retry.attemptNo()).isEqualTo(2);
        assertThat(retry.promptSha256()).isEqualTo(initial.promptSha256());
    }

    @Test
    @DisplayName("Given 缺少 typed InvocationContext When 模型调用 Then 拒绝发送且不落信封")
    void should_reject_send_when_invocation_context_missing() {
        // 准备参数
        var middleware = middleware();

        // 调用 + 断言
        assertThatThrownBy(
                        () ->
                                middleware
                                        .onModelCall(
                                                null,
                                                RuntimeContext.empty(),
                                                input(),
                                                ignored -> Flux.error(new AssertionError("不应继续发送")))
                                        .collectList()
                                        .block())
                .isInstanceOf(IllegalStateException.class);
        verify(envelopes, never()).append(any());
    }

    private PromptEnvelopeCaptureMiddleware middleware() {
        return new PromptEnvelopeCaptureMiddleware(
                envelopes, Clock.fixed(Instant.parse("2026-08-27T10:00:00Z"), ZoneOffset.UTC));
    }

    private static org.mockito.stubbing.Answer<PromptEnvelope> answerWithSeq(int seq) {
        return invocation -> ((PromptEnvelope.Draft) invocation.getArgument(0)).withSeq(seq);
    }

    private PromptEnvelope.Draft capturedDraft() {
        var captor = ArgumentCaptor.forClass(PromptEnvelope.Draft.class);
        verify(envelopes, atLeastOnce()).append(captor.capture());
        return captor.getValue();
    }

    private static RuntimeContext runtimeContext() {
        return new AgentScopeRuntimeContextMapper().toAgentScope(context(), "agent.envelope-test");
    }

    private static ModelCallInput input() {
        var message =
                Msg.builder()
                        .role(MsgRole.USER)
                        .content(TextBlock.builder().text("撰写品牌文案").build())
                        .build();
        var tool =
                ToolSchema.builder()
                        .name("knowledge.search")
                        .description("检索知识库")
                        .parameters(Map.of("type", "object"))
                        .build();
        var options = GenerateOptions.builder().modelName("qwen-max").temperature(0.2).build();
        return new ModelCallInput(List.of(message), List.of(tool), options, null);
    }

    private static InvocationContext context() {
        return new InvocationContext(
                TENANT_ID,
                new UserId("7"),
                null,
                new AssistantId("system.assistant.default-user"),
                new ConversationId("conversation-1"),
                new SessionId("session-1"),
                TASK_ID,
                EXECUTION_ID,
                new RunId("run-1"),
                null,
                new CorrelationId("correlation-1"),
                null,
                new IdempotencyKey("idempotency-1"),
                ControlMode.READ_ONLY,
                null,
                null,
                new ToolAuthorizationContext(Map.of()));
    }
}
