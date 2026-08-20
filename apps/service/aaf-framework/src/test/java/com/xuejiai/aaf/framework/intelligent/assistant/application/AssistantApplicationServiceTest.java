package com.xuejiai.aaf.framework.intelligent.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionCriteria;
import com.xuejiai.aaf.framework.intelligent.assistant.model.MemoryStrategy;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskModelSelection;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.SubjectKind;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.DefaultUserAssistantTemplate;
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

class AssistantApplicationServiceTest {

    @Test
    @DisplayName("Given 助理开启长期记忆且主体是登录用户 When 判断是否启用 Then 启用")
    void should_enable_long_term_memory_for_user_subject() {
        var definition = definitionWithLongTermMemory(true);
        var command = command(userSubject("20"), new UserId("20"));

        assertThat(AssistantApplicationService.longTermMemoryEnabled(command, definition)).isTrue();
    }

    @Test
    @DisplayName("Given 助理开启长期记忆但主体是访客 When 判断是否启用 Then 降级为短期上下文不启用")
    void should_disable_long_term_memory_for_visitor_subject_even_if_assistant_allows_it() {
        var definition = definitionWithLongTermMemory(true);
        var command = command(visitorSubject("channel-visitor-1"), new UserId("999"));

        assertThat(AssistantApplicationService.longTermMemoryEnabled(command, definition))
                .isFalse();
    }

    @Test
    @DisplayName("Given 助理未开启长期记忆且主体是登录用户 When 判断是否启用 Then 不启用")
    void should_disable_long_term_memory_when_assistant_does_not_allow_it() {
        var definition = definitionWithLongTermMemory(false);
        var command = command(userSubject("20"), new UserId("20"));

        assertThat(AssistantApplicationService.longTermMemoryEnabled(command, definition))
                .isFalse();
    }

    private static AssistantDefinition definitionWithLongTermMemory(boolean longTermEnabled) {
        var template = new DefaultUserAssistantTemplate().templates().getFirst();
        var strategy =
                longTermEnabled ? MemoryStrategy.hybridDefault() : MemoryStrategy.knowledgeOnly();
        return new AssistantDefinition(
                template.assistantId(),
                template.systemKey(),
                template.sourceSystemKey(),
                template.ownership(),
                template.version(),
                template.maintainer(),
                template.actor(),
                template.roles(),
                template.assistantSkillBindings(),
                template.assistantToolKeys(),
                template.defaultRoleKey(),
                strategy,
                template.modelId(),
                template.toolPolicy(),
                template.supportedControlModes(),
                template.defaultRiskPolicy(),
                template.lifecycle());
    }

    private static MemorySubject userSubject(String userId) {
        return new MemorySubject(new TenantId("10"), SubjectKind.USER, userId);
    }

    private static MemorySubject visitorSubject(String visitorId) {
        return new MemorySubject(new TenantId("10"), SubjectKind.VISITOR, visitorId);
    }

    private static AssistantCommand command(MemorySubject memorySubject, UserId userId) {
        return new AssistantCommand(
                AssistantCommand.Operation.START,
                new TenantId("10"),
                userId,
                memorySubject,
                new AssistantId("system.assistant.default-user"),
                new ConversationId("conversation-1"),
                new SessionId("session-1"),
                new TaskId("task-1"),
                new ExecutionId("execution-1"),
                new RunId("run-1"),
                null,
                new CorrelationId("correlation-1"),
                null,
                new IdempotencyKey("idempotency-1"),
                ControlMode.READ_ONLY,
                null,
                null,
                0,
                "你好",
                CompletionCriteria.responseDelivered(),
                List.of(),
                TaskModelSelection.auto(),
                InvocationProfile.primary(
                        null,
                        AssistantInvocation.MemoryMode.DEFAULT,
                        List.of(),
                        com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent
                                .conversationalAuto(null)),
                Instant.parse("2026-08-01T12:00:00Z"));
    }
}
