package com.xuejiai.aaf.framework.intelligent.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionCriteria;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DecompositionBudget;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskModelSelection;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.SubjectKind;
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

/**
 * 协调计划的授权衰减边界。
 *
 * <p>规划模型只能在委派方自身已冻结的 Role/Skill 内组合，不得放大到基准之外——即使模型输出了越界值也必须 fail-closed。
 */
class DelegatedTaskCoordinatorAuthorizationTest {

    private static final String ROLE = "system.role.content-creator";
    private static final String SKILL = "ip-position";

    @Test
    @DisplayName("Given 计划使用基准内 Role 与 Skill When 解码 Then 通过并冻结执行者")
    void should_accept_plan_within_frozen_baseline() {
        var plan =
                DelegatedTaskCoordinator.decodeAndValidatePlan(
                        command(), board(), planJson(ROLE, SKILL), budget());

        assertThat(plan.executors()).hasSize(1);
        assertThat(plan.executors().getFirst().roleKey()).isEqualTo(ROLE);
        assertThat(plan.executors().getFirst().skillKey()).isEqualTo(SKILL);
    }

    @Test
    @DisplayName("Given 计划越界到基准外 Role When 解码 Then 拒绝提权")
    void should_reject_plan_widening_role_beyond_baseline() {
        assertThatThrownBy(
                        () ->
                                DelegatedTaskCoordinator.decodeAndValidatePlan(
                                        command(),
                                        board(),
                                        planJson("system.role.admin-escalated", SKILL),
                                        budget()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能更改已冻结的 Role 或 Skill");
    }

    @Test
    @DisplayName("Given 计划越界到基准外 Skill When 解码 Then 拒绝提权")
    void should_reject_plan_widening_skill_beyond_baseline() {
        assertThatThrownBy(
                        () ->
                                DelegatedTaskCoordinator.decodeAndValidatePlan(
                                        command(),
                                        board(),
                                        planJson(ROLE, "unauthorized-skill"),
                                        budget()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能更改已冻结的 Role 或 Skill");
    }

    private static DecompositionBudget budget() {
        return DecompositionBudget.defaults();
    }

    private static TaskBoard board() {
        return TaskBoard.coordinated(new TaskId("task-1"), "撰写品牌文案", ROLE, SKILL, 3);
    }

    private static String planJson(String roleKey, String skillKey) {
        return """
                {"goal":"完成品牌文案",
                 "maxParallelism":1,
                 "aggregationContract":{"kind":"PASS_THROUGH","executorOrder":["executor-copy"]},
                 "executors":[{"subTaskId":"executor-copy",
                               "description":"输出 Markdown 文案",
                               "roleKey":"%s",
                               "skillKey":"%s",
                               "modelMode":"AUTO"}]}
                """
                .formatted(roleKey, skillKey);
    }

    private static AssistantCommand command() {
        var userId = new UserId("7");
        return new AssistantCommand(
                AssistantCommand.Operation.START,
                new TenantId("10"),
                userId,
                new MemorySubject(new TenantId("10"), SubjectKind.USER, userId.value()),
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
                "撰写品牌文案",
                CompletionCriteria.responseDelivered(),
                List.of(),
                TaskModelSelection.auto(),
                InvocationProfile.primary(
                        SKILL,
                        AssistantInvocation.MemoryMode.DEFAULT,
                        List.of(),
                        ExecutionIntent.taskFixed(
                                ROLE,
                                SKILL,
                                1L,
                                ExecutionIntent.ArtifactPolicy.returnOnly(
                                        ExecutionIntent.OutputKind.MESSAGE, "text/markdown"),
                                ExecutionIntent.ActionAuthorizationPolicy.requestOnDemand(),
                                null)),
                Instant.parse("2026-08-01T12:00:00Z"));
    }
}
