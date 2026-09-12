package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionCriteria;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Execution;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionContract;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskCheckpoint;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDispatch;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskMaterializationPort.PauseAckCommand;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.plan.ExecutorPlanRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.trace.persistence.SynchronousExecutionEventWriter;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort.StoredExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

class JpaTaskMaterializationAdapterPausingTest {

    private static final TenantId TENANT = new TenantId("10");
    private static final UserId USER = new UserId("7");
    private static final TaskId TASK_ID = new TaskId("task-1");
    private static final ExecutionId EXECUTION_ID = new ExecutionId("execution-1");
    private static final Instant NOW = Instant.parse("2026-09-03T10:15:30Z");

    @Test
    @DisplayName(
            "Given active Execution When AgentState ACK 成功 Then durable pause 收敛为 same attempt")
    void should_settle_same_attempt_after_state_ack() {
        var fixture = fixture();

        var request =
                fixture.adapter()
                        .requestPause(TENANT, USER, TASK_ID, "用户暂停", Duration.ofSeconds(20), NOW);
        var paused =
                fixture.adapter()
                        .acknowledgePause(
                                new PauseAckCommand(
                                        TENANT,
                                        TASK_ID,
                                        request.requestId(),
                                        EXECUTION_ID,
                                        true,
                                        "state-slot-1",
                                        "agentscope-agent-state-v1",
                                        NOW.plusSeconds(2),
                                        null,
                                        NOW.plusSeconds(2)));
        var duplicate =
                fixture.adapter()
                        .acknowledgePause(
                                new PauseAckCommand(
                                        TENANT,
                                        TASK_ID,
                                        request.requestId(),
                                        EXECUTION_ID,
                                        true,
                                        "state-slot-1",
                                        "agentscope-agent-state-v1",
                                        NOW.plusSeconds(2),
                                        null,
                                        NOW.plusSeconds(3)));

        assertThat(request.created()).isTrue();
        assertThat(request.task().status()).isEqualTo(Task.Status.PAUSING);
        assertThat(request.targets()).containsExactly(EXECUTION_ID);
        assertThat(fixture.dispatch().getDispatch().status())
                .isEqualTo(TaskDispatch.Status.CANCELED);
        assertThat(fixture.dispatch().getDispatch().generation()).isEqualTo(3);
        assertThat(paused.status()).isEqualTo(Task.Status.PAUSED);
        assertThat(duplicate.status()).isEqualTo(Task.Status.PAUSED);
        assertThat(paused.checkpoint().annotations())
                .containsEntry("pauseResumeMode", "SAME_ATTEMPT")
                .containsEntry("freshAttemptRequired", false);
        assertThat(fixture.node().getStatus()).isEqualTo(TaskPlan.Status.PAUSED.name());
        assertThat(fixture.execution().getExecution().status()).isEqualTo(Execution.Status.PAUSED);
    }

    @Test
    @DisplayName(
            "Given partial pause without ACK When deadline 到期 Then 逐 Execution 记失败并降级 fresh attempt")
    void should_settle_fresh_attempt_after_ack_timeout() {
        var fixture = fixture();

        fixture.adapter().requestPause(TENANT, USER, TASK_ID, "用户暂停", Duration.ofSeconds(20), NOW);
        var finalized =
                fixture.adapter().finalizeTimedOutPause(TENANT, TASK_ID, NOW.plusSeconds(21));
        var duplicate =
                fixture.adapter().finalizeTimedOutPause(TENANT, TASK_ID, NOW.plusSeconds(22));

        var paused = fixture.task().getTask();
        assertThat(finalized).isTrue();
        assertThat(duplicate).isFalse();
        assertThat(paused.status()).isEqualTo(Task.Status.PAUSED);
        assertThat(paused.checkpoint().annotations())
                .containsEntry("pauseResumeMode", "FRESH_ATTEMPT")
                .containsEntry("freshAttemptRequired", true)
                .containsEntry("pauseTimedOut", true);
        var pauseAcks = (Map<?, ?>) paused.checkpoint().annotations().get("pauseAcks");
        assertThat(pauseAcks.containsKey(EXECUTION_ID.value())).isTrue();
        assertThat(fixture.node().getStatus()).isEqualTo(TaskPlan.Status.RETRYABLE.name());
        assertThat(fixture.execution().getExecution().status()).isEqualTo(Execution.Status.PAUSED);
    }

    private static Fixture fixture() {
        var tasks = mock(TaskRootRepository.class);
        var plans = mock(TaskPlanRepository.class);
        var nodes = mock(TaskNodeRepository.class);
        var dependencies = mock(TaskDependencyRepository.class);
        var executions = mock(TaskExecutionRepository.class);
        var dispatches = mock(TaskDispatchRepository.class);
        var inputs = mock(TaskInputRepository.class);
        var executorPlans = mock(ExecutorPlanRepository.class);
        var eventWriter = mock(SynchronousExecutionEventWriter.class);
        var outbox = mock(TaskTransitionOutboxRepository.class);
        var adapter =
                new JpaTaskMaterializationAdapter(
                        tasks,
                        plans,
                        nodes,
                        dependencies,
                        executions,
                        dispatches,
                        inputs,
                        executorPlans,
                        eventWriter,
                        outbox);

        var taskEntity = new TaskRootEntity();
        taskEntity.setTask(task());
        var planEntity = mock(TaskPlanEntity.class);
        var nodeEntity = new TaskNodeEntity();
        nodeEntity.setTenantId(TENANT.value());
        nodeEntity.setTaskId(TASK_ID.value());
        nodeEntity.setPlanId("plan-1");
        nodeEntity.setPlanRevision(1);
        nodeEntity.setNodeId("worker");
        nodeEntity.setStatus(TaskPlan.Status.RUNNING.name());
        nodeEntity.setCurrentExecutionId(EXECUTION_ID.value());
        nodeEntity.setCurrentSessionId("session-1");
        nodeEntity.setCurrentAttempt(1);
        nodeEntity.setClarifiedParameters(Map.of());
        var executionEntity = new TaskExecutionEntity();
        executionEntity.setExecution(execution());
        var dispatchEntity = new TaskDispatchEntity();
        dispatchEntity.setDispatch(dispatch());

        when(tasks.findForUpdate(TENANT.value(), TASK_ID.value()))
                .thenReturn(Optional.of(taskEntity));
        when(tasks.saveAndFlush(any(TaskRootEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(plans.findCurrentForUpdate(TENANT.value(), TASK_ID.value(), "plan-1", 1))
                .thenReturn(Optional.of(planEntity));
        when(nodes.findPlanNodesForUpdate(TENANT.value(), TASK_ID.value(), "plan-1", 1))
                .thenReturn(List.of(nodeEntity));
        when(executions.findForUpdate(TENANT.value(), EXECUTION_ID.value()))
                .thenReturn(Optional.of(executionEntity));
        when(dispatches.findActiveForUpdate(TENANT.value(), EXECUTION_ID.value()))
                .thenReturn(Optional.of(dispatchEntity), Optional.empty());
        when(dispatches.nextFence()).thenReturn(11L);
        when(outbox.findByOutboxId(any())).thenReturn(Optional.empty());
        when(eventWriter.append(any(ExecutionEvent.class), anyLong()))
                .thenAnswer(
                        invocation -> {
                            var event = invocation.getArgument(0, ExecutionEvent.class);
                            return new SynchronousExecutionEventWriter.WriteResult(
                                    new StoredExecutionEvent(1, event), true);
                        });
        return new Fixture(adapter, taskEntity, nodeEntity, executionEntity, dispatchEntity);
    }

    private static Task task() {
        return new Task(
                TENANT,
                USER,
                TASK_ID,
                new ConversationId("conversation-1"),
                null,
                null,
                null,
                "input-ref",
                "context-ref",
                Task.Source.CONVERSATION,
                0,
                Task.Status.RUNNING,
                ControlMode.COLLABORATIVE,
                new Task.Owner(Task.OwnerKind.ASSISTANT, "assistant-1"),
                ExecutionContract.conversationDefault(
                        Set.of("respond"),
                        new ExecutionContract.ResponsibleOwner("AI", "assistant-1")),
                CompletionCriteria.responseDelivered(),
                Task.BudgetUsage.empty(),
                "plan-1",
                1,
                null,
                0,
                null,
                TaskCheckpoint.empty(),
                null,
                NOW,
                NOW);
    }

    private static Execution execution() {
        return new Execution(
                TENANT,
                USER,
                new ConversationId("conversation-1"),
                TASK_ID,
                "plan-1",
                1,
                "worker",
                EXECUTION_ID,
                new SessionId("session-1"),
                new RunId("run-1"),
                new CorrelationId("correlation-1"),
                null,
                null,
                Execution.Scope.TASK_NODE,
                1,
                "state-slot-1",
                Execution.Status.RUNNING,
                Execution.PromotionState.INELIGIBLE,
                0,
                new Task.Owner(Task.OwnerKind.ASSISTANT, "assistant-1"),
                0,
                NOW,
                NOW);
    }

    private static TaskDispatch dispatch() {
        return new TaskDispatch(
                "dispatch-1",
                TENANT,
                EXECUTION_ID,
                TaskDispatch.Status.CLAIMED,
                NOW,
                "worker-1",
                NOW.plusSeconds(60),
                2,
                10,
                1,
                null,
                0,
                NOW,
                NOW);
    }

    private record Fixture(
            JpaTaskMaterializationAdapter adapter,
            TaskRootEntity task,
            TaskNodeEntity node,
            TaskExecutionEntity execution,
            TaskDispatchEntity dispatch) {}
}
