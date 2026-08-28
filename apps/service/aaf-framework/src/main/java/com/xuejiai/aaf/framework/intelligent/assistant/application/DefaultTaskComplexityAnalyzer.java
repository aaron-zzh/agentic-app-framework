package com.xuejiai.aaf.framework.intelligent.assistant.application;

import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskAnalysis;

/**
 * 确定性优先的复杂度判定实现。
 *
 * <p>只在确定性信号不足且目标足够长时才需要升级为模型判定；当前实现全程确定性，不产生任何模型调用，因此简单单句请求不增加首字延迟与成本。
 *
 * <p><b>fail-safe 方向</b>：判定倾向 {@code SINGLE_AGENT}。协调编排要求存在可用于协调节点的已解析 Role 与
 * Skill，缺失时一律降级为单执行体并记录原因， 不得为了拆分而放宽授权边界。
 */
public class DefaultTaskComplexityAnalyzer implements TaskComplexityAnalyzer {

    /** 低于该长度的请求视为简单请求，直接短路。 */
    private static final int SIMPLE_GOAL_MAX_CHARS = 60;

    /** 命中多少个多目标信号词才认为存在拆分价值。 */
    private static final int MULTI_GOAL_HIT_THRESHOLD = 2;

    @Override
    public TaskAnalysis analyze(AnalysisInput input) {
        if (input.workflowKey() != null && !input.workflowKey().isBlank()) {
            return new TaskAnalysis(
                    TaskAnalysis.OwnerMode.DELEGATE,
                    TaskAnalysis.ProcessMode.PREDEFINED_WORKFLOW,
                    TaskAnalysis.CoordinationMode.TASKBOARD,
                    "已发布工作流 %s 决定过程形态，不做复杂度判定".formatted(input.workflowKey()),
                    TaskAnalysis.AnalyzedBy.DETERMINISTIC);
        }

        var goal = input.goal();

        // 确定性短路：短单句、无附件、无材料一律单执行体，不调模型
        if (goal.length() <= SIMPLE_GOAL_MAX_CHARS
                && input.attachmentCount() == 0
                && input.materialCount() == 0) {
            return TaskAnalysis.singleAgent("目标为短单句且无附件与材料，确定性判为单执行体");
        }

        var multiGoalHits = countMultiGoalMarkers(goal);
        var hasDecompositionValue =
                multiGoalHits >= MULTI_GOAL_HIT_THRESHOLD
                        || input.attachmentCount() >= 2
                        || input.materialCount() >= 2;

        if (!hasDecompositionValue) {
            return TaskAnalysis.singleAgent(
                    "多目标信号命中 %d 次，附件 %d、材料 %d，未达拆分阈值"
                            .formatted(
                                    multiGoalHits, input.attachmentCount(), input.materialCount()));
        }

        return TaskAnalysis.taskBoard(
                "多目标信号命中 %d 次，附件 %d、材料 %d，判为协调编排"
                        .formatted(multiGoalHits, input.attachmentCount(), input.materialCount()));
    }

    private int countMultiGoalMarkers(String goal) {
        if (goal.isEmpty()) {
            return 0;
        }
        var hits = 0;
        for (var marker : MULTI_GOAL_MARKERS) {
            if (goal.contains(marker)) {
                hits++;
            }
        }
        return hits;
    }
}
