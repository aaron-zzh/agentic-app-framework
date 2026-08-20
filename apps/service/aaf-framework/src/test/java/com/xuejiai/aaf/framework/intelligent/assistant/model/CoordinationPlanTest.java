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
}
