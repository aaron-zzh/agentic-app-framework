package com.xuejiai.aaf.framework.intelligent.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.assistant.application.CompletionValidator.ValidationRequest;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionCriteria;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionDecision.Outcome;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskModelSelection;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlanDraft;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;

/** AGGREGATOR 节点完成后，父任务仍必须通过显式业务事件校验。 */
class TaskPlanAggregatorCompletionTest {

    private final DefaultCompletionValidator validator = new DefaultCompletionValidator();

    @Test
    @DisplayName("Given AGGREGATOR 结果缺少必需业务事件 When 完成判定 Then 要求修复而非直接完成")
    void should_require_repair_when_aggregator_completion_evidence_is_missing() {
        var decision =
                validator.validate(
                        new ValidationRequest(
                                DefaultCompletionValidatorTest.task(),
                                CompletionCriteria.responseDelivered(),
                                Optional.of(completedAggregatorPlan()),
                                List.of(
                                        DefaultCompletionValidatorTest.event(
                                                1, ExecutionEventType.RUN_COMPLETED, Map.of()))));

        assertThat(decision.outcome()).isEqualTo(Outcome.CONTINUE_REPAIR);
        assertThat(decision.reason()).contains("MESSAGE_COMPLETED");
    }

    @Test
    @DisplayName("Given AGGREGATOR 结果满足必需业务事件 When 完成判定 Then 正常完成")
    void should_complete_when_aggregator_completion_evidence_is_satisfied() {
        var decision =
                validator.validate(
                        new ValidationRequest(
                                DefaultCompletionValidatorTest.task(),
                                CompletionCriteria.responseDelivered(),
                                Optional.of(completedAggregatorPlan()),
                                List.of(
                                        DefaultCompletionValidatorTest.event(
                                                1, ExecutionEventType.MESSAGE_COMPLETED, Map.of()),
                                        DefaultCompletionValidatorTest.event(
                                                2, ExecutionEventType.RUN_COMPLETED, Map.of()),
                                        DefaultCompletionValidatorTest.event(
                                                3,
                                                ExecutionEventType.EXECUTION_COMPLETED,
                                                Map.of()))));

        assertThat(decision.outcome()).isEqualTo(Outcome.COMPLETED);
    }

    private static TaskPlan completedAggregatorPlan() {
        var executor =
                new TaskPlan.TaskNode(
                        "executor-copy",
                        TaskPlan.TaskNode.Kind.EXECUTOR,
                        "输出品牌文案草稿。",
                        Set.of(),
                        Map.of(),
                        "system.role.content-creator",
                        "ip-position",
                        null,
                        TaskModelSelection.auto(),
                        TaskPlan.Status.COMPLETED,
                        true,
                        1,
                        1,
                        new ExecutionId("executor-execution-1"),
                        new SessionId("executor-session-1"),
                        "品牌文案草稿",
                        null,
                        Map.of());
        var aggregator =
                new TaskPlan.TaskNode(
                        "aggregator",
                        TaskPlan.TaskNode.Kind.AGGREGATOR,
                        "根据已完成执行者结果生成最终聚合输出。",
                        Set.of("executor-copy"),
                        Map.of(
                                "result.executor-copy",
                                new TaskPlanDraft.InputBinding("executor-copy")),
                        "system.role.content-creator",
                        "ip-position",
                        null,
                        TaskModelSelection.auto(),
                        TaskPlan.Status.RUNNING,
                        true,
                        1,
                        1,
                        new ExecutionId("aggregator-execution-1"),
                        new SessionId("aggregator-session-1"),
                        null,
                        null,
                        Map.of());
        var running =
                new TaskPlan(
                        new TaskId("task-1"),
                        new TaskPlan.Goal(
                                "goal",
                                "完成品牌文案",
                                Set.of("aggregator"),
                                new TaskPlanDraft.AggregationContract(
                                        TaskPlanDraft.AggregationContract.Kind.AGGREGATOR_REDUCE,
                                        List.of("executor-copy"),
                                        ""),
                                null),
                        1,
                        Map.of(
                                executor.nodeId(), executor,
                                aggregator.nodeId(), aggregator));
        return running.complete("aggregator", "最终品牌文案");
    }
}
