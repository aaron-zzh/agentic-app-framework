package com.xuejiai.aaf.framework.intelligent.assistant.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

class ExecutionResumePolicyTest {

    @Test
    @DisplayName("Given PAUSED execution identity 与状态槽兼容 When 决策 Then 复用 same attempt")
    void should_resume_same_attempt_when_identity_and_state_match() {
        var execution = execution();

        var decision =
                new ExecutionResumePolicy()
                        .decide(
                                new ExecutionResumePolicy.Request(
                                        execution, true, true, true, true));

        assertThat(decision).isEqualTo(ExecutionResumePolicy.Decision.SAME_ATTEMPT);
    }

    @Test
    @DisplayName("Given PAUSED execution 状态槽缺失 When 决策 Then 创建 fresh attempt")
    void should_require_fresh_attempt_when_state_is_missing() {
        var execution = execution();

        var decision =
                new ExecutionResumePolicy()
                        .decide(
                                new ExecutionResumePolicy.Request(
                                        execution, true, true, false, true));

        assertThat(decision).isEqualTo(ExecutionResumePolicy.Decision.FRESH_ATTEMPT);
    }

    private static Execution execution() {
        var at = Instant.parse("2026-09-03T10:15:30Z");
        return new Execution(
                new TenantId("10"),
                new UserId("7"),
                new ConversationId("conversation-1"),
                new TaskId("task-1"),
                "plan-1",
                1,
                "worker",
                new ExecutionId("execution-1"),
                new SessionId("session-1"),
                new RunId("run-1"),
                new CorrelationId("correlation-1"),
                null,
                null,
                Execution.Scope.TASK_NODE,
                1,
                "state-slot-1",
                Execution.Status.PAUSED,
                Execution.PromotionState.INELIGIBLE,
                0,
                new Task.Owner(Task.OwnerKind.ASSISTANT, "assistant-1"),
                0,
                at,
                at);
    }
}
