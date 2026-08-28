package com.xuejiai.aaf.framework.intelligent.assistant.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.assistant.model.CoordinationPlan.ExecutorAssignment;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;

class CoordinationPlanTest {

    @Test
    @DisplayName("Given 固定文案任务 When 协调计划冻结 Then 先完成协调者并创建独立执行者节点")
    void should_create_executor_after_coordinator_plan_is_frozen() {
        var board =
                TaskBoard.coordinated(
                        new TaskId("task-1"),
                        "撰写品牌文案",
                        "system.role.content-creator",
                        "ip-position",
                        3);
        var coordinator = board.claimReady();
        var plan =
                new CoordinationPlan(
                        "完成品牌文案",
                        1,
                        CoordinationPlan.AggregationContract.passThrough("executor-copy"),
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

        var frozen = coordinator.board().applyCoordinationPlan(plan);

        assertThat(frozen.subTasks().get("coordinator").status())
                .isEqualTo(TaskBoard.Status.COMPLETED);
        assertThat(frozen.subTasks().get("executor-copy").kind())
                .isEqualTo(TaskBoard.SubTask.Kind.EXECUTOR);
        assertThat(frozen.subTasks().get("executor-copy").modelSelection().mode())
                .isEqualTo(TaskModelSelection.Mode.AUTO);
        assertThat(frozen.claimReady().subTasks())
                .extracting(TaskBoard.SubTask::subTaskId)
                .containsExactly("executor-copy");
    }

    @Test
    @DisplayName("Given 空执行者计划 When 创建 Then 拒绝协调者绕过执行 Agent")
    void should_reject_plan_without_executor() {
        assertThatThrownBy(
                        () ->
                                new CoordinationPlan(
                                        "目标",
                                        1,
                                        CoordinationPlan.AggregationContract.passThrough(
                                                "executor"),
                                        List.of(),
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("执行者");
    }

    @Test
    @DisplayName("Given 固定 Team When 计划并行度低于冻结 roster Then 拒绝静默串行化")
    void should_reject_team_plan_narrowing_parallelism_below_roster() {
        var board = teamBoard();
        var coordinator = board.claimReady();
        var plan = teamPlan(1);

        assertThatThrownBy(() -> coordinator.board().applyCoordinationPlan(plan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("并行度必须等于冻结 roster");
    }

    @Test
    @DisplayName("Given 固定 Team When 计划并行度等于冻结 roster Then 冻结成功并保留槽位")
    void should_accept_team_plan_matching_roster_parallelism() {
        var board = teamBoard();
        var coordinator = board.claimReady();

        var frozen = coordinator.board().applyCoordinationPlan(teamPlan(2));

        assertThat(frozen.maxParallelism()).isEqualTo(2);
        assertThat(frozen.claimReady().subTasks())
                .extracting(TaskBoard.SubTask::subTaskId)
                .containsExactlyInAnyOrder("worker-a", "worker-b");
    }

    private static TaskBoard teamBoard() {
        return TaskBoard.teamCoordinated(
                new TaskId("task-team"),
                "完成品牌方案",
                target("assistant-leader", "system.role.leader", "planning"),
                Map.of(
                        "worker-a", target("assistant-a", "system.role.content-creator", "copy"),
                        "worker-b", target("assistant-b", "system.role.reviewer", "review")),
                3);
    }

    private static CoordinationPlan teamPlan(int maxParallelism) {
        return new CoordinationPlan(
                "完成品牌方案",
                maxParallelism,
                CoordinationPlan.AggregationContract.orderedConcat(
                        List.of("worker-a", "worker-b"), "\n"),
                List.of(
                        assignment("worker-a", "system.role.content-creator", "copy"),
                        assignment("worker-b", "system.role.reviewer", "review")),
                null);
    }

    private static ExecutorAssignment assignment(
            String subTaskId, String roleKey, String skillKey) {
        return new ExecutorAssignment(
                subTaskId,
                "执行 " + subTaskId,
                Set.of("coordinator"),
                Map.of(),
                roleKey,
                skillKey,
                TaskModelSelection.auto(),
                3);
    }

    private static TaskBoard.AssistantTarget target(
            String assistantId, String roleKey, String skillKey) {
        return new TaskBoard.AssistantTarget(assistantId, 0L, roleKey, skillKey, Set.of());
    }
}
