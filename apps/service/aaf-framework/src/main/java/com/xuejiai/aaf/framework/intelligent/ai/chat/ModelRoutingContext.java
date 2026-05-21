package com.xuejiai.aaf.framework.intelligent.ai.chat;

import java.util.Map;

/**
 * 模型路由上下文，携带决策所需的全部信息。
 *
 * <p>决策链优先级（从高到低）：
 * <ol>
 *   <li>显式指定 — 调用方直接传 modelId
 *   <li>编排引擎配置 — 工作流节点 / AgentDefinition 绑定的模型
 *   <li>AI 辅助决策 — 根据任务特征智能选模型
 *   <li>用户偏好 — 用户为该能力设置的默认模型
 *   <li>系统默认 — 管理员配置的全局默认
 *   <li>yaml 兜底 — AiProperties.defaultModel
 * </ol>
 *
 * @param userId              当前用户 ID（用于查用户偏好）
 * @param capability          能力类型：CHAT / EMBEDDING / IMAGE_GEN / SPEECH_ASR 等
 * @param explicitModelId     调用方显式指定的 modelId（最高优先级，可为 null）
 * @param orchestrationModelId 编排引擎（工作流节点/Agent）绑定的 modelId（可为 null）
 * @param taskFeatures        任务特征，用于 AI 辅助决策（可为 null）
 */
public record ModelRoutingContext(
        Long userId,
        String capability,
        String explicitModelId,
        String orchestrationModelId,
        Map<String, Object> taskFeatures) {

    /** 简化构造：只有显式 modelId，无编排配置和任务特征 */
    public static ModelRoutingContext of(Long userId, String capability, String explicitModelId) {
        return new ModelRoutingContext(userId, capability, explicitModelId, null, null);
    }

    /** 简化构造：纯能力路由，无任何显式配置 */
    public static ModelRoutingContext ofCapability(Long userId, String capability) {
        return new ModelRoutingContext(userId, capability, null, null, null);
    }

    /** 任务特征 key 常量 */
    public static final String FEATURE_INPUT_LENGTH = "inputLength";
    public static final String FEATURE_HAS_IMAGE = "hasImage";
    public static final String FEATURE_HAS_VIDEO = "hasVideo";
    public static final String FEATURE_COST_SENSITIVE = "costSensitive";
    public static final String FEATURE_REASONING_REQUIRED = "reasoningRequired";
}
