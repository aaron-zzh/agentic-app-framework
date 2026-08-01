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
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;
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
    @DisplayName("Given 有效技能顺序不稳定且提示重复 When 合并 Then 按优先级和标识稳定排序并去重")
    void should_merge_skill_prompts_deterministically() {
        var skills =
                List.of(
                        skill(2L, "技能 B", "提示 B", 10),
                        skill(4L, "重复技能", "提示 A", 5),
                        skill(3L, "高优技能", "提示 高", 20),
                        skill(1L, "技能 A", " 提示 A ", 10),
                        skill(5L, "空提示", "   ", 30));

        var result = AssistantApplicationService.mergeSkillPrompts(skills);

        assertThat(result).isEqualTo("提示 高\n\n提示 A\n\n提示 B");
    }

    @Test
    @DisplayName("Given 技能没有可用系统提示 When 合并 Then 返回空附录")
    void should_return_empty_appendix_when_skill_prompts_are_unavailable() {
        var skills = List.of(skill(1L, "空白", " ", 10), skill(2L, "缺失", null, 20));

        var result = AssistantApplicationService.mergeSkillPrompts(skills);

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

        assertThat(AssistantApplicationService.longTermMemoryEnabled(command, definition)).isFalse();
    }

    @Test
    @DisplayName("Given 助理未开启长期记忆且主体是登录用户 When 判断是否启用 Then 不启用")
    void should_disable_long_term_memory_when_assistant_does_not_allow_it() {
        var definition = definitionWithLongTermMemory(false);
        var command = command(userSubject("20"), new UserId("20"));

        assertThat(AssistantApplicationService.longTermMemoryEnabled(command, definition)).isFalse();
    }

    private static SkillDef skill(Long id, String name, String systemPrompt, int priority) {
        return new SkillDef(id, name, name + "描述", null, List.of(), systemPrompt, priority, false);
    }

    private static AssistantDefinition definitionWithLongTermMemory(boolean longTermEnabled) {
        var template = new DefaultUserAssistantTemplate().templates().getFirst();
        var strategy =
                longTermEnabled
                        ? MemoryStrategy.hybridDefault()
                        : MemoryStrategy.knowledgeOnly();
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
                template.skillRoutes(),
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
                new com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantVersion(1),
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
