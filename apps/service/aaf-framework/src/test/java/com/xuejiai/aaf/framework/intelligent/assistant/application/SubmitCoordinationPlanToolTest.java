package com.xuejiai.aaf.framework.intelligent.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DecompositionBudget;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskModelSelection;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlanDraft;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskPlanPort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.event.NodeIdentity;
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

/** 协调计划只能在委派方冻结的 Role/Skill 内组合，不得通过结构化工具调用扩大授权。 */
class SubmitCoordinationPlanToolTest {

    private static final String ROLE = "system.role.content-creator";
    private static final String SKILL = "ip-position";
    private static final TenantId TENANT = new TenantId("10");
    private static final TaskId TASK = new TaskId("task-1");

    @Test
    @DisplayName("Given 计划使用基准内 Role 与 Skill When 提交 Then 通过并冻结执行者")
    void should_accept_plan_within_frozen_baseline() {
        var tool = tool(taskPlan());

        var result = tool.invoke(invocation(ROLE, SKILL)).block();

        assertThat(result.output()).contains("已提交并生效");
        assertThat(result.metadata().get("executorCount")).isEqualTo(1);
    }

    @Test
    @DisplayName("Given 计划越界到基准外 Role When 提交 Then 拒绝提权")
    void should_reject_plan_widening_role_beyond_baseline() {
        var tool = tool(taskPlan());

        assertThatThrownBy(
                        () -> tool.invoke(invocation("system.role.admin-escalated", SKILL)).block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能更改已冻结的 Role 或 Skill");
    }

    @Test
    @DisplayName("Given 计划越界到基准外 Skill When 提交 Then 拒绝提权")
    void should_reject_plan_widening_skill_beyond_baseline() {
        var tool = tool(taskPlan());

        assertThatThrownBy(() -> tool.invoke(invocation(ROLE, "unauthorized-skill")).block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能更改已冻结的 Role 或 Skill");
    }

    private static SubmitCoordinationPlanTool tool(TaskPlan taskPlan) {
        return new SubmitCoordinationPlanTool(
                new StubTaskPlanPort(taskPlan), DecompositionBudget.defaults());
    }

    /** 工具只允许运行中的 coordinator 提交 draft，fixture 直接物化 canonical 节点身份。 */
    private static TaskPlan taskPlan() {
        var coordinator =
                new TaskPlan.TaskNode(
                        "coordinator",
                        TaskPlan.TaskNode.Kind.COORDINATOR,
                        "撰写品牌文案",
                        Set.of(),
                        Map.of(),
                        ROLE,
                        SKILL,
                        null,
                        TaskModelSelection.auto(),
                        TaskPlan.Status.RUNNING,
                        true,
                        1,
                        3,
                        new ExecutionId("coordinator-execution-1"),
                        new SessionId("coordinator-session-1"),
                        null,
                        null,
                        Map.of());
        return new TaskPlan(
                TASK,
                new TaskPlan.Goal(
                        "goal",
                        "撰写品牌文案",
                        Set.of("coordinator"),
                        TaskPlanDraft.AggregationContract.passThrough("coordinator"),
                        null),
                1,
                Map.of(coordinator.nodeId(), coordinator));
    }

    private static ToolInvocation invocation(String roleKey, String skillKey) {
        var arguments =
                Map.<String, Object>of(
                        "goal",
                        "完成品牌文案",
                        "maxParallelism",
                        1,
                        "aggregationContract",
                        Map.of("kind", "PASS_THROUGH", "executorOrder", List.of("executor-copy")),
                        "executors",
                        List.of(
                                Map.of(
                                        "nodeId", "executor-copy",
                                        "description", "输出 Markdown 文案",
                                        "roleKey", roleKey,
                                        "skillKey", skillKey,
                                        "modelMode", "AUTO")));
        return new ToolInvocation(
                "tool-call-1",
                new ToolRef(
                        SubmitCoordinationPlanTool.TOOL_NAME,
                        1,
                        SubmitCoordinationPlanTool.TOOL_NAME),
                arguments,
                context());
    }

    private static InvocationContext context() {
        var userId = new UserId("7");
        var lease =
                new ConversationLeasePort.Lease(
                        TENANT,
                        new ConversationId("conversation-1"),
                        "owner-1",
                        1L,
                        Instant.parse("2026-08-01T13:00:00Z"));
        return new InvocationContext(
                TENANT,
                userId,
                null,
                new AssistantId("system.assistant.default-user"),
                new ConversationId("conversation-1"),
                new SessionId("session-1"),
                TASK,
                new ExecutionId("execution-1"),
                new RunId("run-1"),
                null,
                new CorrelationId("correlation-1"),
                new CausationId("causation-1"),
                new IdempotencyKey("idempotency-1"),
                ControlMode.DELEGATED,
                com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionContract
                        .conversationDefault(
                                Set.of("respond"),
                                new com.xuejiai.aaf.framework.intelligent.assistant.model
                                        .ExecutionContract.ResponsibleOwner("AI", "system")),
                lease,
                new ToolAuthorizationContext(Map.of()),
                new NodeIdentity(
                        "coordinator", NodeIdentity.NodeKind.COORDINATOR, ROLE, SKILL, false));
    }

    /** 只实现本测试实际使用的 find/applyTaskPlanDraft；其余调用均视为测试设计错误。 */
    private static final class StubTaskPlanPort implements TaskPlanPort {
        private TaskPlan taskPlan;

        StubTaskPlanPort(TaskPlan taskPlan) {
            this.taskPlan = taskPlan;
        }

        @Override
        public Optional<TaskPlan> find(TenantId tenantId, TaskId taskId) {
            return Optional.of(taskPlan);
        }

        @Override
        public TaskPlan update(
                TenantId tenantId, TaskPlan plan, ConversationLeasePort.Lease lease) {
            throw new UnsupportedOperationException("未使用");
        }

        @Override
        public TaskPlan applyTaskPlanDraft(
                TenantId tenantId,
                TaskId taskId,
                TaskPlanDraft draft,
                ConversationLeasePort.Lease lease) {
            taskPlan = taskPlan.applyTaskPlanDraft(draft);
            return taskPlan;
        }

        @Override
        public TaskPlan completeNode(
                TenantId tenantId,
                TaskId taskId,
                String nodeId,
                String result,
                ConversationLeasePort.Lease lease) {
            throw new UnsupportedOperationException("未使用");
        }

        @Override
        public TaskPlan failNode(
                TenantId tenantId,
                TaskId taskId,
                String nodeId,
                String failure,
                boolean transientFailure,
                ConversationLeasePort.Lease lease) {
            throw new UnsupportedOperationException("未使用");
        }

        @Override
        public TaskPlan interruptNode(
                TenantId tenantId,
                TaskId taskId,
                String nodeId,
                boolean retryable,
                ConversationLeasePort.Lease lease) {
            throw new UnsupportedOperationException("未使用");
        }

        @Override
        public TaskPlan interruptRunning(
                TenantId tenantId,
                TaskId taskId,
                boolean retryable,
                ConversationLeasePort.Lease lease) {
            throw new UnsupportedOperationException("未使用");
        }
    }
}
