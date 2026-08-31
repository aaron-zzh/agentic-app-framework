package com.xuejiai.aaf.framework.intelligent.infrastructure.workflow.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Optional;

import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantDefinitionFixtures;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantCommandPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventPayload;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.EventId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

import reactor.core.publisher.Flux;

/**
 * Agent 节点：以当前触发用户身份构造单轮任务式 Assistant 调用。
 *
 * <p>覆盖角色技能可选配置（未配置回退默认角色）、执行成功取正文、执行失败场景。
 */
class AgentNodeTest extends BaseMockitoUnitTest {

    @Mock private AssistantCommandPort assistants;
    @Mock private AssistantDefinitionPort assistantDefinitions;
    @Mock private DelegateExecution execution;

    private AgentNode node;

    @Test
    @DisplayName("Given 节点未配置 roleKey When 执行 Then 回退到 Assistant 默认角色")
    void should_fallback_to_default_role_when_role_key_not_configured() {
        node = new AgentNode(assistants, assistantDefinitions);
        stubExecutionVariables("你好", null, null);
        when(assistantDefinitions.findDefaultForUser(any(), any()))
                .thenReturn(Optional.of(AssistantDefinitionFixtures.defaultUser()));
        var captor = ArgumentCaptor.forClass(AssistantCommand.class);
        when(assistants.execute(captor.capture())).thenReturn(completedEvents("你好呀"));

        node.execute(execution);

        verify(execution).setVariable("output", "你好呀");
        verify(execution).setVariable("success", true);
        var resolvedRoute = captor.getValue().invocationProfile().executionIntent().resolvedRoute();
        assertThat(resolvedRoute.roleKey())
                .isEqualTo(AssistantDefinitionFixtures.PLATFORM_GUIDE_ROLE_KEY);
        assertThat(resolvedRoute.skillKey()).isNull();
    }

    @Test
    @DisplayName("Given 节点显式配置 roleKey 与 skillKey When 执行 Then 使用节点配置而非默认角色")
    void should_use_configured_role_and_skill_when_present() {
        node = new AgentNode(assistants, assistantDefinitions);
        stubExecutionVariables(
                "生成摘要", AssistantDefinitionFixtures.CONTENT_CREATOR_ROLE_KEY, "aigc-copywriting");
        when(assistantDefinitions.findDefaultForUser(any(), any()))
                .thenReturn(Optional.of(AssistantDefinitionFixtures.defaultUser()));
        var captor = ArgumentCaptor.forClass(AssistantCommand.class);
        when(assistants.execute(captor.capture())).thenReturn(completedEvents("摘要内容"));

        node.execute(execution);

        verify(execution).setVariable("output", "摘要内容");
        var resolvedRoute = captor.getValue().invocationProfile().executionIntent().resolvedRoute();
        assertThat(resolvedRoute.roleKey())
                .isEqualTo(AssistantDefinitionFixtures.CONTENT_CREATOR_ROLE_KEY);
        assertThat(resolvedRoute.skillKey()).isEqualTo("aigc-copywriting");
    }

    @Test
    @DisplayName("Given Assistant 执行未完成 When 执行 Then 标记失败而非向上抛异常")
    void should_mark_failure_when_execution_not_completed() {
        node = new AgentNode(assistants, assistantDefinitions);
        stubExecutionVariables("你好", null, null);
        when(assistantDefinitions.findDefaultForUser(any(), any()))
                .thenReturn(Optional.of(AssistantDefinitionFixtures.defaultUser()));
        when(assistants.execute(any())).thenReturn(Flux.just(failedEvent()));

        node.execute(execution);

        verify(execution).setVariable("success", false);
        verify(execution).setVariable("error", "Assistant 工作流节点执行未完成: FAILED");
    }

    @Test
    @DisplayName("Given 用户默认 Assistant 不存在 When 执行 Then 标记失败")
    void should_mark_failure_when_default_assistant_missing() {
        node = new AgentNode(assistants, assistantDefinitions);
        when(execution.getVariable("input")).thenReturn("你好");
        when(execution.getVariable("promptOverride")).thenReturn(null);
        when(execution.getVariable("_aafOrgId")).thenReturn("1");
        when(execution.getVariable("_aafUserId")).thenReturn("test");
        when(assistantDefinitions.findDefaultForUser(any(), any())).thenReturn(Optional.empty());

        node.execute(execution);

        verify(execution).setVariable("success", false);
        verify(execution).setVariable("error", "用户默认 Assistant 不存在: test");
    }

    private void stubExecutionVariables(String input, String roleKey, String skillKey) {
        when(execution.getVariable("input")).thenReturn(input);
        when(execution.getVariable("promptOverride")).thenReturn(null);
        when(execution.getVariable("_aafOrgId")).thenReturn("1");
        when(execution.getVariable("_aafUserId")).thenReturn("test");
        when(execution.getVariable("roleKey")).thenReturn(roleKey);
        when(execution.getVariable("skillKey")).thenReturn(skillKey);
        when(execution.getProcessInstanceId()).thenReturn("proc-1");
        when(execution.getCurrentActivityId()).thenReturn("activity-1");
    }

    private Flux<ExecutionEvent> completedEvents(String text) {
        var messageValues = new LinkedHashMap<String, Object>();
        messageValues.put("text", text);
        var message =
                event(
                        ExecutionEventType.MESSAGE_COMPLETED,
                        ExecutionEvent.ExecutionEventStatus.COMPLETED,
                        messageValues);
        var terminal =
                event(
                        ExecutionEventType.EXECUTION_COMPLETED,
                        ExecutionEvent.ExecutionEventStatus.COMPLETED,
                        new LinkedHashMap<>());
        return Flux.just(message, terminal);
    }

    private ExecutionEvent failedEvent() {
        var values = new LinkedHashMap<String, Object>();
        values.put("error", "执行失败");
        return event(
                ExecutionEventType.EXECUTION_FAILED,
                ExecutionEvent.ExecutionEventStatus.FAILED,
                values);
    }

    private ExecutionEvent event(
            ExecutionEventType type,
            ExecutionEvent.ExecutionEventStatus status,
            LinkedHashMap<String, Object> values) {
        return new ExecutionEvent(
                new EventId("event-" + type),
                new TenantId("1"),
                new ConversationId("workflow:proc-1"),
                new SessionId("workflow:proc-1"),
                new TaskId("workflow:proc-1"),
                new ExecutionId("workflow:x"),
                new RunId("workflow:x"),
                null,
                1,
                type,
                status,
                ControlMode.COLLABORATIVE,
                OwnerType.ASSISTANT,
                new AssistantId("test.assistant.default-user"),
                null,
                new UserId("test"),
                new CorrelationId("workflow:proc-1"),
                null,
                null,
                new ExecutionEventPayload(values),
                Instant.now());
    }
}
