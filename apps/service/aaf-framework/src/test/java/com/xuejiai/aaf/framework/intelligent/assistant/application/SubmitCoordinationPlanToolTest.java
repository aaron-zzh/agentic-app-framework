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
import com.xuejiai.aaf.framework.intelligent.assistant.model.CoordinationPlan;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DecompositionBudget;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskModelSelection;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskBoardPort;
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

/**
 * 协调计划的授权衰减边界（迁移自已删除的 {@code DelegatedTaskCoordinator.decodeAndValidatePlan}，业务规则随
 * {@link SubmitCoordinationPlanTool} 原样迁移——计划提交方式从"输出严格 JSON 文本 + 手工解析"改为"调用工具"，
 * 见 dev-log 方案变更记录）。
 *
 * <p>规划模型只能在委派方自身已冻结的 Role/Skill 内组合，不得放大到基准之外——即使模型输出了越界值也必须 fail-closed。
 */
class SubmitCoordinationPlanToolTest {

    private static final String ROLE = "system.role.content-creator";
    private static final String SKILL = "ip-position";
    private static final TenantId TENANT = new TenantId("10");
    private static final TaskId TASK = new TaskId("task-1");

    @Test
    @DisplayName("Given 计划使用基准内 Role 与 Skill When 提交 Then 通过并冻结执行者")
    void should_accept_plan_within_frozen_baseline() {
        var tool = tool(board());

        var result = tool.invoke(invocation(ROLE, SKILL)).block();

        assertThat(result.output()).contains("已提交并生效");
        assertThat(result.metadata().get("executorCount")).isEqualTo(1);
    }

    @Test
    @DisplayName("Given 计划越界到基准外 Role When 提交 Then 拒绝提权")
    void should_reject_plan_widening_role_beyond_baseline() {
        var tool = tool(board());

        assertThatThrownBy(
                        () ->
                                tool.invoke(invocation("system.role.admin-escalated", SKILL))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能更改已冻结的 Role 或 Skill");
    }

    @Test
    @DisplayName("Given 计划越界到基准外 Skill When 提交 Then 拒绝提权")
    void should_reject_plan_widening_skill_beyond_baseline() {
        var tool = tool(board());

        assertThatThrownBy(
                        () -> tool.invoke(invocation(ROLE, "unauthorized-skill")).block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能更改已冻结的 Role 或 Skill");
    }

    private static SubmitCoordinationPlanTool tool(TaskBoard board) {
        return new SubmitCoordinationPlanTool(
                new StubTaskBoardPort(board), DecompositionBudget.defaults());
    }

    /**
     * 协调者节点必须处于 {@code RUNNING} 才能提交计划（{@code TaskBoard.applyCoordinationPlan} 前置校验），
     * 不能用 {@code TaskBoard.coordinated(...)} 静态工厂——它产出的协调者初始态是 {@code PENDING}（等待调度领取），
     * 需要手工构造一个已处于 {@code RUNNING} 的协调者节点，与 {@code
     * DelegatedTaskCoordinatorAggregatorCompletionTest} 已确立的 fixture 模式一致。
     */
    private static TaskBoard board() {
        var coordinator =
                new TaskBoard.SubTask(
                        "coordinator",
                        TaskBoard.SubTask.Kind.COORDINATOR,
                        "撰写品牌文案",
                        Set.of(),
                        Map.of(),
                        ROLE,
                        SKILL,
                        null,
                        TaskModelSelection.auto(),
                        TaskBoard.Status.RUNNING,
                        true,
                        1,
                        3,
                        new ExecutionId("coordinator-execution-1"),
                        new SessionId("coordinator-session-1"),
                        null,
                        null,
                        Map.of());
        return new TaskBoard(
                TASK,
                new TaskBoard.Goal(
                        "goal",
                        "撰写品牌文案",
                        Set.of("coordinator"),
                        CoordinationPlan.AggregationContract.passThrough("coordinator"),
                        null),
                1,
                Map.of(coordinator.subTaskId(), coordinator));
    }

    private static ToolInvocation invocation(String roleKey, String skillKey) {
        var arguments =
                Map.<String, Object>of(
                        "goal", "完成品牌文案",
                        "maxParallelism", 1,
                        "aggregationContract",
                                Map.of(
                                        "kind", "PASS_THROUGH",
                                        "executorOrder", List.of("executor-copy")),
                        "executors",
                                List.of(
                                        Map.of(
                                                "subTaskId", "executor-copy",
                                                "description", "输出 Markdown 文案",
                                                "roleKey", roleKey,
                                                "skillKey", skillKey,
                                                "modelMode", "AUTO")));
        return new ToolInvocation(
                "tool-call-1",
                new ToolRef(SubmitCoordinationPlanTool.TOOL_NAME, 1, SubmitCoordinationPlanTool.TOOL_NAME),
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
                                java.util.Set.of("respond"),
                                new com.xuejiai.aaf.framework.intelligent.assistant.model
                                        .ExecutionContract.ResponsibleOwner("AI", "system")),
                lease,
                new ToolAuthorizationContext(Map.of()),
                new NodeIdentity("coordinator", NodeIdentity.NodeKind.COORDINATOR, ROLE, SKILL, false));
    }

    /** 只实现本测试用到的两个方法；其它方法调用即测试设计错误，直接抛异常暴露。 */
    private static final class StubTaskBoardPort implements TaskBoardPort {
        private TaskBoard board;

        StubTaskBoardPort(TaskBoard board) {
            this.board = board;
        }

        @Override
        public TaskBoard save(TenantId tenantId, TaskBoard board) {
            throw new UnsupportedOperationException("未使用");
        }

        @Override
        public Optional<TaskBoard> find(TenantId tenantId, TaskId taskId) {
            return Optional.of(board);
        }

        @Override
        public TaskBoard.ReadyClaim claimReady(TenantId tenantId, TaskId taskId, ConversationLeasePort.Lease lease) {
            throw new UnsupportedOperationException("未使用");
        }

        @Override
        public TaskBoard applyCoordinationPlan(
                TenantId tenantId, TaskId taskId, CoordinationPlan plan, ConversationLeasePort.Lease lease) {
            board = board.applyCoordinationPlan(plan);
            return board;
        }

        @Override
        public TaskBoard completeSubTask(
                TenantId tenantId, TaskId taskId, String subTaskId, String result, ConversationLeasePort.Lease lease) {
            throw new UnsupportedOperationException("未使用");
        }

        @Override
        public TaskBoard failSubTask(
                TenantId tenantId,
                TaskId taskId,
                String subTaskId,
                String failure,
                boolean transientFailure,
                ConversationLeasePort.Lease lease) {
            throw new UnsupportedOperationException("未使用");
        }

        @Override
        public TaskBoard interruptSubTask(
                TenantId tenantId, TaskId taskId, String subTaskId, boolean retryable, ConversationLeasePort.Lease lease) {
            throw new UnsupportedOperationException("未使用");
        }

        @Override
        public TaskBoard interruptRunning(
                TenantId tenantId, TaskId taskId, boolean retryable, ConversationLeasePort.Lease lease) {
            throw new UnsupportedOperationException("未使用");
        }
    }
}
