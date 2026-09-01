package com.xuejiai.aaf.framework.intelligent.assistant.application;

/**
 * 是否需要先规划再执行的最终判定（ADR-006 补充决策二）。
 *
 * <p>本接口判"要不要走规划状态机"；"规划提交后是否要人工审"这一层已被 ADR-006「决策推翻」（2026-09-01）取消——提交计划不新增
 * 执行面、不引入新的 Agent 身份，风险已由协调者派发子节点时的既有审批点与步骤执行阶段的工具授权链路覆盖，不再需要独立的自动批准白名单判定。
 *
 * <p>协调者可以给出建议信号（{@code coordinatorSuggestsPlan}），地位与 {@code submit_executor_plan} 的 {@code
 * risks} 字段相同——仅供参考，不直接决定。最终结果必须由本接口的实现给出，且不得直接返回协调者的建议值（否则等同于把决策权交给模型，
 * 与议题四"模型自评不作门控输入"同类风险）。
 */
public interface PlanRequirementPolicy {

    /**
     * 判定某个节点在本轮是否需要先规划再执行。
     *
     * @param nodeSubTaskId 板上节点标识（协调者或执行者皆可，ADR-006 补充决策一已放宽适用范围）
     * @param roleKey 该节点冻结的角色键
     * @param skillKey 该节点冻结的技能键（可空）
     * @param coordinatorSuggestsPlan 协调者给出的建议信号；{@code null} 表示协调者未给出建议（如根节点直接执行场景）
     * @return 最终判定结果
     */
    boolean requiresPlan(
            String nodeSubTaskId, String roleKey, String skillKey, Boolean coordinatorSuggestsPlan);

    /** 默认实现：完全尊重协调者建议，未给出建议时默认不规划。用于尚未配置任何强制/豁免规则的最小可用起点。 */
    static PlanRequirementPolicy respectCoordinatorSuggestion() {
        return (nodeSubTaskId, roleKey, skillKey, coordinatorSuggestsPlan) ->
                Boolean.TRUE.equals(coordinatorSuggestsPlan);
    }
}
