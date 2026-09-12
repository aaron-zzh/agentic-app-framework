package com.xuejiai.aaf.framework.intelligent.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CausationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.IdempotencyKey;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

import reactor.core.publisher.Mono;

class TaskConversationControlToolTest {

    private static final TenantId TENANT = new TenantId("10");
    private static final UserId USER = new UserId("7");
    private static final ConversationId CONVERSATION = new ConversationId("conversation-1");
    private static final TaskId TASK_ID = new TaskId("task-1");
    private static final Instant NOW = Instant.parse("2026-09-03T10:15:30Z");

    @Test
    @DisplayName("Given 子智能体分工调整 When amend_task Then 写入 TASK_PLAN MODIFY")
    void should_submit_task_plan_amendment() {
        var tasks = mock(TaskCommandService.class);
        var task = task(Task.Status.RUNNING, 2);
        when(tasks.resolveControllableTask(TENANT, USER, CONVERSATION, null)).thenReturn(task);
        when(tasks.acceptInput(any())).thenReturn(Mono.just(task));
        var tool = new AmendTaskTool(tasks, Clock.fixed(NOW, ZoneOffset.UTC));

        var result =
                tool.invoke(
                                invocation(
                                        AmendTaskTool.TOOL_NAME,
                                        Map.of("scope", "TASK_PLAN", "change", "删除校对节点")))
                        .block();

        var input = ArgumentCaptor.forClass(ExecutionInput.class);
        verify(tasks).acceptInput(input.capture());
        assertThat(input.getValue().kind()).isEqualTo(ExecutionInput.Kind.MODIFY);
        assertThat(input.getValue().text()).isEqualTo("删除校对节点");
        assertThat(input.getValue().values()).containsEntry("scope", "TASK_PLAN");
        assertThat(input.getValue().receivedAt()).isEqualTo(NOW);
        assertThat(result.metadata()).containsEntry("currentPlanRevision", 2);
    }

    @Test
    @DisplayName("Given 局部步骤调整未指定节点 When amend_task Then 拒绝模糊修改")
    void should_require_node_for_executor_plan_amendment() {
        var tool =
                new AmendTaskTool(mock(TaskCommandService.class), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(
                        () ->
                                tool.invoke(
                                                invocation(
                                                        AmendTaskTool.TOOL_NAME,
                                                        Map.of(
                                                                "scope",
                                                                "EXECUTOR_PLAN",
                                                                "change",
                                                                "交换前两步")))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须明确 nodeId");
    }

    @Test
    @DisplayName("Given 明确停止指令 When cancel_task Then 停止解析后的 canonical Task")
    void should_cancel_resolved_task() {
        var tasks = mock(TaskCommandService.class);
        var running = task(Task.Status.RUNNING, 3);
        var canceling = task(Task.Status.CANCELING, 3);
        when(tasks.resolveControllableTask(TENANT, USER, CONVERSATION, "task-1"))
                .thenReturn(running);
        when(tasks.cancel(TENANT, USER, TASK_ID, "用户不再需要")).thenReturn(canceling);
        var tool = new CancelTaskTool(tasks);

        var result =
                tool.invoke(
                                invocation(
                                        CancelTaskTool.TOOL_NAME,
                                        Map.of("taskId", "task-1", "reason", "用户不再需要")))
                        .block();

        verify(tasks).cancel(TENANT, USER, TASK_ID, "用户不再需要");
        assertThat(result.metadata()).containsEntry("taskStatus", "CANCELING");
        assertThat(result.output()).contains("进入取消流程");
        assertThat(result.output()).contains("用户业务数据均已保留");
    }

    private static Task task(Task.Status status, Integer revision) {
        var task = mock(Task.class);
        when(task.taskId()).thenReturn(TASK_ID);
        when(task.status()).thenReturn(status);
        when(task.currentPlanRevision()).thenReturn(revision);
        return task;
    }

    private static ToolInvocation invocation(String toolName, Map<String, Object> arguments) {
        return new ToolInvocation(
                "tool-call-1",
                new ToolRef(toolName, 1, toolName),
                arguments,
                new InvocationContext(
                        TENANT,
                        USER,
                        null,
                        new AssistantId("system.assistant.default-user"),
                        CONVERSATION,
                        new SessionId("session-1"),
                        null,
                        new ExecutionId("execution-1"),
                        new RunId("run-1"),
                        null,
                        new CorrelationId("correlation-1"),
                        new CausationId("causation-1"),
                        new IdempotencyKey("idempotency-1"),
                        ControlMode.READ_ONLY,
                        null,
                        null,
                        new ToolAuthorizationContext(Map.of())));
    }
}
