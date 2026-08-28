package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;

import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskAnalysis;

/**
 * 任务复杂度判定器——决定本次运行使用 single 还是 coordinated。
 *
 * <p>判定顺序遵循量入为出：<b>先确定性短路，再模型判定</b>。无附件、无多目标信号、无跨领域动作的单句请求必须在不调用模型的情况下判为 {@code SINGLE_AGENT}。
 */
public interface TaskComplexityAnalyzer {

    /**
     * 判定本次目标的编排形态。
     *
     * @param input 判定输入
     * @return 不可变判定结果，随执行画像冻结
     */
    TaskAnalysis analyze(AnalysisInput input);

    /**
     * 判定输入。
     *
     * @param goal 用户本轮目标正文
     * @param attachmentCount 用户附件数量
     * @param materialCount 任务材料数量
     * @param routeResolved 是否已解析出可用于协调节点的 Role 与 Skill
     * @param workflowKey 已发布工作流 key；非空时过程形态固定为 PREDEFINED_WORKFLOW
     */
    record AnalysisInput(
            String goal,
            int attachmentCount,
            int materialCount,
            boolean routeResolved,
            String workflowKey) {

        public AnalysisInput {
            goal = goal == null ? "" : goal.trim();
            if (attachmentCount < 0 || materialCount < 0) {
                throw new IllegalArgumentException("附件与材料数量不能为负数");
            }
        }

        public static AnalysisInput of(String goal, boolean routeResolved) {
            return new AnalysisInput(goal, 0, 0, routeResolved, null);
        }
    }

    /** 多目标信号词，用于确定性判定。 */
    List<String> MULTI_GOAL_MARKERS =
            List.of(
                    "分别", "并且", "同时", "然后", "另外", "此外", "再帮", "顺便", "以及", "对比", "比较", "汇总", "整合",
                    "逐个", "依次", "每个", "各自", "多个", "批量");
}
