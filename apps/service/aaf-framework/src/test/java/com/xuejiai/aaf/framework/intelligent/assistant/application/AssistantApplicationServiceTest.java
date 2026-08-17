package com.xuejiai.aaf.framework.intelligent.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionCriteria;
import com.xuejiai.aaf.framework.intelligent.assistant.model.MemoryStrategy;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskModelSelection;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.SubjectKind;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillVersionRef;
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
    @DisplayName("Given 有效技能内容顺序固定且内容重复 When 合并 Then 保持输入顺序并去重")
    void should_merge_skill_content_deterministically() {
        var skills =
                List.of(
                        skill(2L, "skill-b", "技能 B", "提示 B"),
                        skill(4L, "duplicate", "重复技能", "提示 A"),
                        skill(3L, "high", "高优技能", "提示 高"),
                        skill(1L, "skill-a", "技能 A", " 提示 A "));

        var result = AssistantApplicationService.mergeSkillPrompts(skills);

        assertThat(result).isEqualTo("提示 B\n\n提示 A\n\n提示 高");
    }

    @Test
    @DisplayName("Given 没有有效技能 When 合并 Then 返回空附录")
    void should_return_empty_appendix_when_skills_are_unavailable() {
        var result = AssistantApplicationService.mergeSkillPrompts(List.of());

        assertThat(result).isEmpty();
    }

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

    private static SkillDef skill(Long id, String code, String name, String content) {
        return new SkillDef(
                id,
                code,
                name,
                name + "描述",
                new SkillVersionRef(id, id, 1),
                content,
                Set.of(),
                Set.of(),
                false);
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
                Instant.parse("2026-08-01T12:00:00Z"));
    }
}
