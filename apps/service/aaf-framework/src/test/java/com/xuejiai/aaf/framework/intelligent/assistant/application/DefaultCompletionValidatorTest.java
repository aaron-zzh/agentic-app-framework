package com.xuejiai.aaf.framework.intelligent.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.assistant.application.CompletionValidator.ValidationRequest;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionCriteria;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionDecision.Outcome;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionContract;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskCheckpoint;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventPayload;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.EventId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

class DefaultCompletionValidatorTest {

    private final DefaultCompletionValidator validator = new DefaultCompletionValidator();

    @Test
    void draftToolEvidenceCompletesTask() {
        var decision =
                validator.validate(
                        new ValidationRequest(
                                task(),
                                CompletionCriteria.reversibleDraftCreated(),
                                Optional.empty(),
                                List.of(
                                        event(
                                                1,
                                                ExecutionEventType.TOOL_CALL_COMPLETED,
                                                Map.of(
                                                        "artifactState",
                                                        "DRAFT",
                                                        "reversible",
                                                        true)),
                                        event(2, ExecutionEventType.RUN_COMPLETED, Map.of()))));

        assertThat(decision.outcome()).isEqualTo(Outcome.COMPLETED);
    }

    @Test
    void missingDraftEvidenceRequiresRepair() {
        var decision =
                validator.validate(
                        new ValidationRequest(
                                task(),
                                CompletionCriteria.reversibleDraftCreated(),
                                Optional.empty(),
                                List.of(event(1, ExecutionEventType.RUN_COMPLETED, Map.of()))));

        assertThat(decision.outcome()).isEqualTo(Outcome.CONTINUE_REPAIR);
        assertThat(decision.reason()).contains("TOOL_CALL_COMPLETED");
    }

    static Task task() {
        var now = Instant.parse("2026-08-19T00:00:00Z");
        return new Task(
                new TenantId("tenant-1"),
                new UserId("user-1"),
                new TaskId("task-1"),
                new ConversationId("conversation-1"),
                null,
                null,
                null,
                "input-ref",
                "context-ref",
                Task.Source.CONVERSATION,
                0,
                Task.Status.VERIFYING,
                ControlMode.COLLABORATIVE,
                new Task.Owner(Task.OwnerKind.ASSISTANT, "assistant-1"),
                ExecutionContract.conversationDefault(
                        Set.of("respond"),
                        new ExecutionContract.ResponsibleOwner("AI", "assistant-1")),
                CompletionCriteria.responseDelivered(),
                Task.BudgetUsage.empty(),
                null,
                null,
                null,
                0,
                null,
                TaskCheckpoint.empty(),
                null,
                now,
                now);
    }

    static ExecutionEvent event(
            long sequence, ExecutionEventType type, Map<String, Object> payload) {
        var executionId = "execution-1";
        return new ExecutionEvent(
                new EventId("event-" + sequence),
                new TenantId("tenant-1"),
                new ConversationId("conversation-1"),
                new SessionId(executionId),
                new TaskId("task-1"),
                new ExecutionId(executionId),
                new RunId(executionId),
                null,
                sequence,
                type,
                ExecutionEventStatus.COMPLETED,
                ControlMode.COLLABORATIVE,
                OwnerType.SYSTEM,
                null,
                null,
                null,
                new CorrelationId(executionId),
                null,
                null,
                new ExecutionEventPayload(payload),
                Instant.parse("2026-08-19T00:00:00Z"));
    }
}
