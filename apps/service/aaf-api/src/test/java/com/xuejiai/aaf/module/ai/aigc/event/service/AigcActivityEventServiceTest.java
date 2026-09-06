package com.xuejiai.aaf.module.ai.aigc.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.module.ai.aigc.event.api.AigcActivityEnvelope;
import com.xuejiai.aaf.module.ai.aigc.event.api.SubscriptionScope;
import com.xuejiai.aaf.module.ai.aigc.event.domain.AigcActivityEvent;
import com.xuejiai.aaf.module.ai.aigc.event.repository.AigcActivityEventRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class AigcActivityEventServiceTest extends BaseMockitoUnitTest {

    @Mock private AigcActivityEventRepository repository;
    @InjectMocks private AigcActivityEventService service;

    @Test
    @DisplayName("Given owner org workspace scope When subscribe Then 锁定水位后按三维作用域重放再注册 live")
    void should_replay_to_upper_bound_before_registering_live_subscriber() {
        // 准备参数
        var scope = new SubscriptionScope(7L, 8L, 9L);
        when(repository.replayUpperBound(7L, 8L, 9L)).thenReturn(20L);
        when(repository.replay(any(), any(), any(), any(), any(), any()))
                .thenAnswer(
                        ignored -> {
                            assertThat(service.subscriberCount(scope)).isZero();
                            return List.of();
                        });

        // 调用
        service.subscribe(scope, 12L);

        // 断言
        var ordered = org.mockito.Mockito.inOrder(repository);
        ordered.verify(repository).replayUpperBound(7L, 8L, 9L);
        ordered.verify(repository)
                .replay(
                        org.mockito.ArgumentMatchers.eq(7L),
                        org.mockito.ArgumentMatchers.eq(8L),
                        org.mockito.ArgumentMatchers.eq(9L),
                        org.mockito.ArgumentMatchers.eq(12L),
                        org.mockito.ArgumentMatchers.eq(20L),
                        any());
        assertThat(service.subscriberCount(scope)).isOne();
    }

    @Test
    @DisplayName("Given live 回调乱序 When 高 ID 先到 Then 从 DB 按 ID 补齐且不重复")
    void should_catch_up_committed_events_in_id_order_when_callbacks_are_out_of_order()
            throws Exception {
        // 准备参数
        var scope = new SubscriptionScope(7L, 8L, 9L);
        var emitter = mock(SseEmitter.class);
        var event21 = event(21L, scope);
        var event22 = event(22L, scope);
        when(repository.replayUpperBound(7L, 8L, 9L)).thenReturn(20L, 22L, 22L);
        when(repository.replay(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(), List.of(event21, event22));
        service.subscribe(scope, 12L, emitter);

        // 调用
        service.broadcast(event22);
        service.broadcast(event21);

        // 断言：connected comment 一次，已提交事件按 21、22 各发送一次
        verify(emitter, times(3)).send(any(SseEmitter.SseEventBuilder.class));
        verify(repository)
                .replay(
                        org.mockito.ArgumentMatchers.eq(7L),
                        org.mockito.ArgumentMatchers.eq(8L),
                        org.mockito.ArgumentMatchers.eq(9L),
                        org.mockito.ArgumentMatchers.eq(20L),
                        org.mockito.ArgumentMatchers.eq(22L),
                        any());
    }

    @Test
    @DisplayName("Given 事件由其他实例提交 When heartbeat Then 从 DB cursor 补齐并保持去重")
    void should_catch_up_database_cursor_on_heartbeat_without_local_broadcast() throws Exception {
        // 准备参数
        var scope = new SubscriptionScope(7L, 8L, 9L);
        var emitter = mock(SseEmitter.class);
        var event21 = event(21L, scope);
        when(repository.replayUpperBound(7L, 8L, 9L)).thenReturn(20L, 21L, 21L);
        when(repository.replay(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(event21));
        service.subscribe(scope, 20L, emitter);

        // 调用
        service.heartbeat();
        service.heartbeat();

        // 断言：connected、event21、两次 heartbeat，event21 仅由首次 catch-up 查询取得
        verify(emitter, times(4)).send(any(SseEmitter.SseEventBuilder.class));
        verify(repository, times(1))
                .replay(
                        org.mockito.ArgumentMatchers.eq(7L),
                        org.mockito.ArgumentMatchers.eq(8L),
                        org.mockito.ArgumentMatchers.eq(9L),
                        org.mockito.ArgumentMatchers.eq(20L),
                        org.mockito.ArgumentMatchers.eq(21L),
                        any());
    }

    @Test
    @DisplayName("Given Activity scope When publish Then 同 scope advisory lock 先于事件持久化")
    void should_lock_scope_before_persisting_activity() {
        // 准备参数
        OrgContext.setCurrentOrgId(8L);
        OrgContext.setCurrentWorkspaceId(9L);
        when(repository.saveAndFlush(any(AigcActivityEvent.class)))
                .thenAnswer(
                        invocation -> {
                            var event = invocation.getArgument(0, AigcActivityEvent.class);
                            event.setId(21L);
                            return event;
                        });

        try {
            // 调用
            var event = service.publish(7L, "task.completed", Map.of("status", "SUCCESS"));

            // 断言
            var ordered = org.mockito.Mockito.inOrder(repository);
            ordered.verify(repository).lockScope(7L, 8L, 9L);
            ordered.verify(repository).saveAndFlush(event);
            assertThat(event.getOrgId()).isEqualTo(8L);
            assertThat(event.getWorkspaceId()).isEqualTo(9L);
        } finally {
            OrgContext.clear();
        }
    }

    @Test
    @DisplayName("Given Activity envelope When JSON 序列化 Then 只暴露统一 wire 字段名")
    void should_expose_single_typed_wire_envelope() {
        // 准备参数
        var envelope =
                new AigcActivityEnvelope(
                        1L,
                        "task.completed",
                        2L,
                        3L,
                        4L,
                        5L,
                        6L,
                        7L,
                        8L,
                        9L,
                        10L,
                        Map.of("status", "SUCCESS"));

        // 调用
        var json = JsonUtils.toJsonString(envelope);

        // 断言
        assertThat(json)
                .contains(
                        "\"projectId\":2",
                        "\"executionRunId\":3",
                        "\"taskId\":4",
                        "\"mediaVersionId\":5",
                        "\"objectVersionId\":6",
                        "\"reviewId\":7",
                        "\"workId\":8",
                        "\"publicationId\":9",
                        "\"conversationId\":10")
                .doesNotContain(
                        "\"project\":",
                        "\"execution\":",
                        "\"task\":",
                        "\"media\":",
                        "\"eventName\":");
    }

    private AigcActivityEvent event(Long id, SubscriptionScope scope) {
        var event = new AigcActivityEvent();
        event.setId(id);
        event.setOwnerId(scope.ownerId());
        event.setOrgId(scope.orgId());
        event.setWorkspaceId(scope.workspaceId());
        event.setEventType("task.completed");
        event.setPayload(Map.of());
        return event;
    }
}
