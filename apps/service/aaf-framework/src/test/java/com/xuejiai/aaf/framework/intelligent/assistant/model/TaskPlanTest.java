package com.xuejiai.aaf.framework.intelligent.assistant.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan.TaskNode;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlanDraft.ExecutorAssignment;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;

class TaskPlanTest {

    private final TaskDagService dag = new TaskDagService();

    @Test
    @DisplayName("Given 固定文案任务 When 协调计划冻结 Then 先完成协调者并创建独立执行者节点")
    void should_create_executor_after_coordinator_plan_is_frozen() {
        var taskPlan =
                runningCoordinator(
                        TaskPlan.coordinated(
                                new TaskId("task-1"),
                                "撰写品牌文案",
                                "system.role.content-creator",
                                "ip-position",
                                3));
        var draft =
                new TaskPlanDraft(
                        "完成品牌文案",
                        1,
                        TaskPlanDraft.AggregationContract.passThrough("executor-copy"),
                        List.of(
                                new ExecutorAssignment(
                                        "executor-copy",
                                        "输出 Markdown 文案并保存草稿",
                                        Set.of("coordinator"),
                                        Map.of(),
                                        "system.role.content-creator",
                                        "ip-position",
                                        TaskModelSelection.auto(),
                                        3)),
                        null);

        var frozen = taskPlan.applyTaskPlanDraft(draft);

        assertThat(frozen.nodes().get("coordinator").status()).isEqualTo(TaskPlan.Status.COMPLETED);
        assertThat(frozen.nodes().get("executor-copy").kind()).isEqualTo(TaskNode.Kind.EXECUTOR);
        assertThat(frozen.nodes().get("executor-copy").modelSelection().mode())
                .isEqualTo(TaskModelSelection.Mode.AUTO);
        assertThat(dag.ready(frozen)).extracting(TaskNode::nodeId).containsExactly("executor-copy");
    }

    @Test
    @DisplayName("Given 空执行者计划 When 创建 Then 拒绝协调者绕过执行 Agent")
    void should_reject_plan_without_executor() {
        assertThatThrownBy(
                        () ->
                                new TaskPlanDraft(
                                        "目标",
                                        1,
                                        TaskPlanDraft.AggregationContract.passThrough("executor"),
                                        List.of(),
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("执行者");
    }

    @Test
    @DisplayName("Given 固定 Team When 计划并行度低于冻结 roster Then 拒绝静默串行化")
    void should_reject_team_plan_narrowing_parallelism_below_roster() {
        var taskPlan = runningCoordinator(teamPlan());

        assertThatThrownBy(() -> taskPlan.applyTaskPlanDraft(draft(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("并行度必须等于冻结 roster");
    }

    @Test
    @DisplayName("Given 固定 Team When 计划并行度等于冻结 roster Then 冻结成功并保留槽位")
    void should_accept_team_plan_matching_roster_parallelism() {
        var taskPlan = runningCoordinator(teamPlan());

        var frozen = taskPlan.applyTaskPlanDraft(draft(2));

        assertThat(frozen.maxParallelism()).isEqualTo(2);
        assertThat(dag.ready(frozen))
                .extracting(TaskNode::nodeId)
                .containsExactlyInAnyOrder("worker-a", "worker-b");
    }

    private static TaskPlan teamPlan() {
        return TaskPlan.teamCoordinated(
                new TaskId("task-team"),
                "完成品牌方案",
                target("assistant-leader", "system.role.leader", "planning"),
                Map.of(
                        "worker-a", target("assistant-a", "system.role.content-creator", "copy"),
                        "worker-b", target("assistant-b", "system.role.reviewer", "review")),
                3);
    }

    private static TaskPlanDraft draft(int maxParallelism) {
        return new TaskPlanDraft(
                "完成品牌方案",
                maxParallelism,
                TaskPlanDraft.AggregationContract.orderedConcat(
                        List.of("worker-a", "worker-b"), "\n"),
                List.of(
                        assignment("worker-a", "system.role.content-creator", "copy"),
                        assignment("worker-b", "system.role.reviewer", "review")),
                null);
    }

    private static ExecutorAssignment assignment(String nodeId, String roleKey, String skillKey) {
        return new ExecutorAssignment(
                nodeId,
                "执行 " + nodeId,
                Set.of("coordinator"),
                Map.of(),
                roleKey,
                skillKey,
                TaskModelSelection.auto(),
                3);
    }

    private static TaskPlan.AssistantTarget target(
            String assistantId, String roleKey, String skillKey) {
        return new TaskPlan.AssistantTarget(assistantId, 0L, roleKey, skillKey, Set.of());
    }

    private static TaskPlan runningCoordinator(TaskPlan taskPlan) {
        var coordinator = taskPlan.nodes().get("coordinator");
        return taskPlan.update(
                new TaskNode(
                        coordinator.nodeId(),
                        coordinator.kind(),
                        coordinator.description(),
                        coordinator.dependsOn(),
                        coordinator.inputBindings(),
                        coordinator.roleKey(),
                        coordinator.skillKey(),
                        coordinator.assistantTarget(),
                        coordinator.modelSelection(),
                        TaskPlan.Status.RUNNING,
                        coordinator.retryable(),
                        1,
                        coordinator.maxAttempts(),
                        new ExecutionId("coordinator-execution-1"),
                        new SessionId("coordinator-session-1"),
                        null,
                        null,
                        Map.of()));
    }
}
