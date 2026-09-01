package com.xuejiai.aaf.framework.intelligent.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.engine.task.agent.AgentTaskRuntime;
import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentExecutionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionCriteria;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CoordinationPlan.AggregationContract;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DecompositionBudget;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard.Goal;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard.Status;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard.SubTask;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard.SubTask.Kind;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskModelSelection;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantCommandPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskDispatchPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.NotificationPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskBoardPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskTransitionPort;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.SubjectKind;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventPayload;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.EventId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.IdempotencyKey;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

import reactor.core.publisher.Flux;

/**
 * 父任务 AGGREGATOR 节点必须与 EXECUTOR 一样经过业务完成证据校验。
 *
 * <p>回归背景：{@code AGGREGATOR_REDUCE} 聚合模式下，Goal 的唯一完成证据是 AGGREGATOR 子任务；此前完成判定分支只对 EXECUTOR 校验
 * {@code completionEvidenceSatisfied}，AGGREGATOR 无条件直接 completeSubTask，导致父任务可以在业务证据不满足的情况下被判定为已完成。
 */
class DelegatedTaskCoordinatorAggregatorCompletionTest {

    private static final TenantId TENANT_ID = new TenantId("10");
    private static final TaskId TASK_ID = new TaskId("task-1");
    private static final String AGGREGATOR_ID = "aggregator";

    @Test
    @DisplayName("Given AGGREGATOR 结果缺少必需业务事件 When 完成判定 Then 转失败而非直接完成")
    void should_fail_aggregator_when_completion_evidence_missing() {
        var boards = mock(TaskBoardPort.class);
        var commands = mock(AssistantCommandPort.class);
        var coordinator = coordinator(commands, boards);

        var aggregatorSubTask = aggregatorSubTask();
        var board = boardWithAggregator(aggregatorSubTask);

        // 子 Agent 只产生 EXECUTION_COMPLETED，缺少 completionCriteria 要求的
        // MESSAGE_COMPLETED / RUN_COMPLETED，模拟聚合结果未满足业务完成证据。
        when(commands.execute(any())).thenReturn(Flux.just(executionCompletedEvent()));

        var events =
                coordinator
                        .executeSubTask(parentCommand(), parentContext(), board, aggregatorSubTask)
                        .collectList()
                        .block();

        assertThat(events).isNotNull();
        verify(boards, never()).completeSubTask(any(), any(), any(), any(), any());
        verify(boards)
                .failSubTask(
                        eq(TENANT_ID), eq(TASK_ID), eq(AGGREGATOR_ID), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("Given AGGREGATOR 结果满足必需业务事件 When 完成判定 Then 正常完成")
    void should_complete_aggregator_when_completion_evidence_satisfied() {
        var boards = mock(TaskBoardPort.class);
        var commands = mock(AssistantCommandPort.class);
        var coordinator = coordinator(commands, boards);

        var aggregatorSubTask = aggregatorSubTask();
        var board = boardWithAggregator(aggregatorSubTask);

        when(commands.execute(any()))
                .thenReturn(
                        Flux.just(
                                messageCompletedEvent(),
                                runCompletedEvent(),
                                executionCompletedEvent()));

        var events =
                coordinator
                        .executeSubTask(parentCommand(), parentContext(), board, aggregatorSubTask)
                        .collectList()
                        .block();

        assertThat(events).isNotNull();
        verify(boards, never()).failSubTask(any(), any(), any(), any(), anyBoolean(), any());
        verify(boards).completeSubTask(eq(TENANT_ID), eq(TASK_ID), eq(AGGREGATOR_ID), any(), any());
    }

    private static DelegatedTaskCoordinator coordinator(
            AssistantCommandPort commands, TaskBoardPort boards) {
        var tasks = mock(DelegatedTaskPort.class);
        var dispatch = mock(DelegatedTaskDispatchPort.class);
        return new DelegatedTaskCoordinator(
                tasks,
                mock(TaskTransitionPort.class),
                new TaskIngress(tasks, dispatch),
                boards,
                mock(ConversationLeasePort.class),
                commands,
                mock(AgentExecutionPort.class),
                mock(NotificationPort.class),
                dispatch,
                mock(AgentTaskRuntime.class),
                DecompositionBudget.defaults(),
                Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), java.time.ZoneOffset.UTC),
                Duration.ofMinutes(5));
    }

    private static SubTask aggregatorSubTask() {
        return new SubTask(
                AGGREGATOR_ID,
                Kind.AGGREGATOR,
                "根据已完成执行者结果生成最终聚合输出。",
                Set.of(),
                Map.of(),
                "system.role.content-creator",
                "ip-position",
                null,
                TaskModelSelection.auto(),
                Status.RUNNING,
                true,
                1,
                1,
                new ExecutionId("aggregator-execution-1"),
                new SessionId("aggregator-session-1"),
                null,
                null,
                Map.of(),
                false);
    }

    private static TaskBoard boardWithAggregator(SubTask aggregatorSubTask) {
        var goal =
                new Goal(
                        "goal",
                        "完成品牌文案",
                        Set.of(AGGREGATOR_ID),
                        new AggregationContract(
                                AggregationContract.Kind.AGGREGATOR_REDUCE,
                                List.of("executor-copy"),
                                ""),
                        null);
        return new TaskBoard(
                TASK_ID, goal, 1, Map.of(aggregatorSubTask.subTaskId(), aggregatorSubTask));
    }

    private static InvocationContext parentContext() {
        return new InvocationContext(
                TENANT_ID,
                new UserId("7"),
                null,
                new AssistantId("system.assistant.default-user"),
                new ConversationId("conversation-1"),
                new SessionId("session-1"),
                TASK_ID,
                new ExecutionId("execution-1"),
                new RunId("run-1"),
                null,
                new CorrelationId("correlation-1"),
                null,
                new IdempotencyKey("idempotency-1"),
                ControlMode.READ_ONLY,
                null,
                null,
                new ToolAuthorizationContext(Map.of()));
    }

    private static AssistantCommand parentCommand() {
        var userId = new UserId("7");
        return new AssistantCommand(
                AssistantCommand.Operation.START,
                TENANT_ID,
                userId,
                new MemorySubject(TENANT_ID, SubjectKind.USER, userId.value()),
                new AssistantId("system.assistant.default-user"),
                new ConversationId("conversation-1"),
                new SessionId("session-1"),
                TASK_ID,
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
                        "ip-position",
                        AssistantInvocation.MemoryMode.DEFAULT,
                        List.of(),
                        ExecutionIntent.taskFixed(
                                "system.role.content-creator",
                                "ip-position",
                                1L,
                                ExecutionIntent.ArtifactPolicy.returnOnly(
                                        ExecutionIntent.OutputKind.MESSAGE, "text/markdown"),
                                ExecutionIntent.ActionAuthorizationPolicy.requestOnDemand(),
                                null)),
                Instant.parse("2026-08-01T12:00:00Z"));
    }

    private static ExecutionEvent executionCompletedEvent() {
        return event(ExecutionEventType.EXECUTION_COMPLETED, ExecutionEventStatus.COMPLETED);
    }

    private static ExecutionEvent messageCompletedEvent() {
        return event(ExecutionEventType.MESSAGE_COMPLETED, ExecutionEventStatus.RUNNING);
    }

    private static ExecutionEvent runCompletedEvent() {
        return event(ExecutionEventType.RUN_COMPLETED, ExecutionEventStatus.RUNNING);
    }

    private static ExecutionEvent event(ExecutionEventType type, ExecutionEventStatus status) {
        return new ExecutionEvent(
                new EventId("event-" + type.name()),
                TENANT_ID,
                new ConversationId("conversation-1"),
                new SessionId("aggregator-session-1"),
                TASK_ID,
                new ExecutionId("aggregator-execution-1"),
                new RunId("run-1"),
                new ExecutionId("execution-1"),
                1,
                type,
                status,
                ControlMode.READ_ONLY,
                OwnerType.ASSISTANT,
                new AssistantId("system.assistant.default-user"),
                null,
                null,
                new CorrelationId("correlation-1"),
                null,
                null,
                ExecutionEventPayload.empty(),
                Instant.parse("2026-08-01T12:00:00Z"));
    }
}
