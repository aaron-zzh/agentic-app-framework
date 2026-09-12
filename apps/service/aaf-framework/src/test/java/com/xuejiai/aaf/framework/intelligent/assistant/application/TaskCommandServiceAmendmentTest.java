package com.xuejiai.aaf.framework.intelligent.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.agent.port.AgentExecutionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantCommandPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ExecutionProfileSnapshotPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HitlTransitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskDispatchSignalPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskMaterializationPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskMaterializationPort.CancelingTask;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskMaterializationPort.PauseRequest;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskMaterializationPort.PausingTask;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskMaterializationPort.ResumeResult;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskUnitOfWork;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

import reactor.core.publisher.Mono;

class TaskCommandServiceAmendmentTest {

    private static final TenantId TENANT = new TenantId("10");
    private static final UserId USER = new UserId("7");
    private static final TaskId TASK_ID = new TaskId("task-1");
    private static final Instant NOW = Instant.parse("2026-09-03T10:15:30Z");

    @Test
    @DisplayName("Given TASK_PLAN MODIFY When 接收输入 Then 启动外层 revision 重规划")
    void should_route_task_plan_amendment() {
        var fixture = fixture();
        var input = input(Map.of("scope", "TASK_PLAN"));

        var result = fixture.service().acceptInput(input).block();

        verify(fixture.materializations())
                .startTaskReplan(TENANT, USER, TASK_ID, "input-1", "调整计划", NOW);
        assertThat(result).isSameAs(fixture.task());
    }

    @Test
    @DisplayName("Given EXECUTOR_PLAN MODIFY When 接收输入 Then 只重启目标节点")
    void should_route_executor_plan_amendment() {
        var fixture = fixture();
        var input = input(Map.of("scope", "EXECUTOR_PLAN", "nodeId", "researcher"));

        var result = fixture.service().acceptInput(input).block();

        verify(fixture.materializations())
                .restartNodeForPlanAmendment(
                        TENANT, USER, TASK_ID, "researcher", "input-1", "调整计划", NOW);
        assertThat(result).isSameAs(fixture.task());
    }

    @Test
    @DisplayName("Given 暂停命令 When runtime 不接受信号 Then 逐 Execution 写失败 ACK")
    void should_request_pause_and_ack_rejected_runtime_signal() {
        var fixture = fixture();
        var executionId = new ExecutionId("execution-1");
        when(fixture.materializations()
                        .requestPause(TENANT, USER, TASK_ID, "用户暂停", Duration.ofSeconds(30), NOW))
                .thenReturn(
                        new PauseRequest(
                                fixture.task(),
                                "pause-1",
                                NOW.plusSeconds(30),
                                List.of(executionId),
                                true));
        when(fixture.agentExecutions().pause(executionId)).thenReturn(Mono.just(false));
        when(fixture.materializations().acknowledgePause(any())).thenReturn(fixture.task());

        var result = fixture.service().pause(TENANT, USER, TASK_ID, "用户暂停");

        verify(fixture.agentExecutions()).pause(executionId);
        verify(fixture.materializations()).acknowledgePause(any());
        assertThat(result).isSameAs(fixture.task());
    }

    @Test
    @DisplayName("Given 已暂停 Task When resume Then 按物化端口冻结模式恢复")
    void should_resume_paused_task_through_materialization_port() {
        var fixture = fixture();
        when(fixture.materializations().resumePaused(TENANT, USER, TASK_ID, NOW))
                .thenReturn(new ResumeResult(fixture.task(), List.of(), false));

        var result = fixture.service().resume(TENANT, USER, TASK_ID);

        verify(fixture.materializations()).resumePaused(TENANT, USER, TASK_ID, NOW);
        assertThat(result).isSameAs(fixture.task());
    }

    @Test
    @DisplayName("Given 取消命令 When cancel Then 只提交 CANCELING 请求")
    void should_request_cancellation_without_synchronous_finalization() {
        var fixture = fixture();
        when(fixture.materializations().requestCancellation(TENANT, USER, TASK_ID, "用户停止", NOW))
                .thenReturn(fixture.task());

        var result = fixture.service().cancel(TENANT, USER, TASK_ID, "用户停止");

        verify(fixture.materializations()).requestCancellation(TENANT, USER, TASK_ID, "用户停止", NOW);
        assertThat(result).isSameAs(fixture.task());
    }

    @Test
    @DisplayName("Given 到期 PAUSING Task When 调度恢复 Then 先降级 fresh attempt 再恢复 Dispatch")
    void should_finalize_timed_out_pausing_tasks_during_recovery() {
        var fixture = fixture();
        when(fixture.materializations().findCanceling(2)).thenReturn(List.of());
        when(fixture.materializations().findPausing(2))
                .thenReturn(List.of(new PausingTask(TENANT, TASK_ID, NOW)));
        when(fixture.materializations().finalizeTimedOutPause(TENANT, TASK_ID, NOW))
                .thenReturn(true);
        when(fixture.materializations().findDue(NOW, 2)).thenReturn(List.of());

        var recovered = fixture.service().recoverAndDispatch("worker-1", 2);

        assertThat(recovered).isEqualTo(1);
        var ordered = org.mockito.Mockito.inOrder(fixture.materializations());
        ordered.verify(fixture.materializations()).findCanceling(2);
        ordered.verify(fixture.materializations()).findPausing(2);
        ordered.verify(fixture.materializations()).finalizeTimedOutPause(TENANT, TASK_ID, NOW);
        ordered.verify(fixture.materializations()).recoverExpired(NOW);
        ordered.verify(fixture.materializations()).findDue(NOW, 2);
    }

    @Test
    @DisplayName("Given 遗留 CANCELING Task When 调度恢复 Then 先终结取消再恢复 Dispatch")
    void should_finalize_stranded_canceling_tasks_during_recovery() {
        var fixture = fixture();
        when(fixture.materializations().findCanceling(2))
                .thenReturn(List.of(new CancelingTask(TENANT, TASK_ID)));
        when(fixture.materializations().finalizeCancellation(TENANT, TASK_ID, NOW))
                .thenReturn(true);
        when(fixture.materializations().recoverExpired(NOW)).thenReturn(1);
        when(fixture.materializations().findDue(NOW, 2)).thenReturn(List.of());

        var recovered = fixture.service().recoverAndDispatch("worker-1", 2);

        assertThat(recovered).isEqualTo(2);
        var ordered = org.mockito.Mockito.inOrder(fixture.materializations());
        ordered.verify(fixture.materializations()).findCanceling(2);
        ordered.verify(fixture.materializations()).finalizeCancellation(TENANT, TASK_ID, NOW);
        ordered.verify(fixture.materializations()).findPausing(2);
        ordered.verify(fixture.materializations()).recoverExpired(NOW);
        ordered.verify(fixture.materializations()).findDue(NOW, 2);
    }

    private static Fixture fixture() {
        var tasks = mock(TaskUnitOfWork.class);
        var ingress = mock(TaskIngress.class);
        var materializations = mock(TaskMaterializationPort.class);
        var agentExecutions = mock(AgentExecutionPort.class);
        var task = mock(Task.class);
        when(task.tenantId()).thenReturn(TENANT);
        when(task.taskId()).thenReturn(TASK_ID);
        when(ingress.accept(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(
                        invocation ->
                                Mono.just(
                                        new TaskIngress.Acceptance(
                                                invocation.getArgument(0), true)));
        when(tasks.findTask(TENANT, TASK_ID)).thenReturn(Optional.of(task));
        when(materializations.startTaskReplan(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(materializations.restartNodeForPlanAmendment(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        var service =
                new TaskCommandService(
                        tasks,
                        ingress,
                        materializations,
                        mock(ExecutionProfileSnapshotPort.class),
                        mock(HitlTransitionPort.class),
                        mock(ConversationLeasePort.class),
                        mock(AssistantCommandPort.class),
                        agentExecutions,
                        mock(TaskDispatchSignalPort.class),
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        Duration.ofMinutes(1),
                        Duration.ofSeconds(30));
        return new Fixture(service, materializations, agentExecutions, task);
    }

    private static ExecutionInput input(Map<String, String> values) {
        return new ExecutionInput(
                "input-1",
                TENANT,
                USER,
                TASK_ID,
                null,
                ExecutionInput.Kind.MODIFY,
                "调整计划",
                values,
                NOW);
    }

    private record Fixture(
            TaskCommandService service,
            TaskMaterializationPort materializations,
            AgentExecutionPort agentExecutions,
            Task task) {}
}
