package com.xuejiai.aaf.framework.agentscope.middleware;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.jdbc.core.JdbcTemplate;

import com.xuejiai.aaf.framework.agentscope.runtime.AafContextHolder;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.middleware.AgentInput;
import reactor.core.publisher.Flux;

/** ConversationBridgeMiddleware 单元测试。 */
class ConversationBridgeMiddlewareTest extends BaseMockitoUnitTest {

    @Mock JdbcTemplate jdbcTemplate;
    @Mock Agent agent;
    @Mock RuntimeContext runtimeContext;

    @AfterEach
    void cleanup() {
        AafContextHolder.clear();
    }

    private ConversationBridgeMiddleware middleware() {
        return new ConversationBridgeMiddleware(jdbcTemplate);
    }

    private Function<AgentInput, Flux<AgentEvent>> nextWith(AgentEvent... events) {
        return input -> Flux.fromArray(events);
    }

    private List<AgentEvent> run(Flux<AgentEvent> flux) {
        return flux.collectList().block();
    }

    @Test
    void onAgent_noConversationId_skipsAllInserts() {
        AafContextHolder.set(new AafContextHolder.AafContext(1L, null, null, null, "t1"));

        var result =
                run(
                        middleware()
                                .onAgent(
                                        agent,
                                        runtimeContext,
                                        new AgentInput(List.of(new UserMessage("hello"))),
                                        nextWith(
                                                new AgentResultEvent(new AssistantMessage("hi")))));

        assertThat(result).hasSize(1);
        verify(jdbcTemplate, never()).update(any(String.class), any(Object[].class));
    }

    @Test
    void onAgent_withConversationId_writesUserAndAssistantMessage() {
        AafContextHolder.set(new AafContextHolder.AafContext(1L, 2L, 100L, null, "t1"));

        run(
                middleware()
                        .onAgent(
                                agent,
                                runtimeContext,
                                new AgentInput(List.of(new UserMessage("hello"))),
                                nextWith(new AgentResultEvent(new AssistantMessage("world")))));

        // user 消息 INSERT（SQL 含 HUMAN）
        verify(jdbcTemplate, times(1)).update(contains("HUMAN"), any(), any(), any());
        // assistant 消息 INSERT（SQL 含 ASSISTANT）
        verify(jdbcTemplate, times(1)).update(contains("ASSISTANT"), any(), any(), any());
    }

    @Test
    void onAgent_emptyUserMessage_skipsUserInsert() {
        AafContextHolder.set(new AafContextHolder.AafContext(1L, null, 100L, null, "t1"));

        // UserMessage 用空白文本，getTextContent() 返回空白，不应 INSERT
        run(
                middleware()
                        .onAgent(
                                agent,
                                runtimeContext,
                                new AgentInput(List.of(new UserMessage("   "))),
                                nextWith(new AgentResultEvent(new AssistantMessage("ok")))));

        verify(jdbcTemplate, never()).update(contains("HUMAN"), any(), any(), any());
    }

    @Test
    void onAgent_noResultEvent_doesNotWriteAssistant() {
        AafContextHolder.set(new AafContextHolder.AafContext(1L, 2L, 100L, null, "t1"));

        run(
                middleware()
                        .onAgent(
                                agent,
                                runtimeContext,
                                new AgentInput(List.of(new UserMessage("hello"))),
                                input -> Flux.empty()));

        verify(jdbcTemplate, times(1)).update(contains("HUMAN"), any(), any(), any());
        verify(jdbcTemplate, never()).update(contains("ASSISTANT"), any(), any(), any());
    }

    @Test
    void onAgent_jdbcThrows_doesNotPropagateError() {
        AafContextHolder.set(new AafContextHolder.AafContext(1L, 2L, 100L, null, "t1"));

        org.mockito.Mockito.doThrow(new RuntimeException("DB 故障"))
                .when(jdbcTemplate)
                .update(any(String.class), any(Object[].class));

        // 不应抛出异常，主事件流正常完成
        var result =
                run(
                        middleware()
                                .onAgent(
                                        agent,
                                        runtimeContext,
                                        new AgentInput(List.of(new UserMessage("hello"))),
                                        nextWith(
                                                new AgentResultEvent(
                                                        new AssistantMessage("reply")))));

        assertThat(result).hasSize(1);
    }
}
