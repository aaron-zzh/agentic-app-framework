package com.xuejiai.aaf.framework.intelligent.core.model;

import java.util.Map;

/**
 * 通用 AI 能力路由上下文。
 *
 * <p>决策链优先级（从高到低）：
 * <ol>
 *   <li>显式指定 — 调用方直接传 modelId
 *   <li>编排引擎配置 — 工作流节点 / AgentDefinition 绑定的模型
 *   <li>AI 辅助决策 — 根据任务特征智能选模型
 *   <li>用户偏好 — 用户为该能力设置的默认模型
 *   <li>系统默认 — 管理员配置的全局默认
 *   <li>yaml 兜底 — 各能力内置默认 modelId
 * </ol>
 *
 * @param userId               当前用户 ID（用于查用户偏好，可为 null）
 * @param capability           能力类型：CHAT / IMAGE_GEN / VIDEO_GEN / SPEECH_ASR / SPEECH_TTS / RERANK / EMBEDDING
 * @param explicitModelId      调用方显式指定的 modelId（最高优先级，可为 null）
 * @param orchestrationModelId 编排引擎（工作流节点/Agent）绑定的 modelId（可为 null）
 * @param taskFeatures         任务特征，用于 AI 辅助决策（可为 null）
 */
public record CapabilityRoutingContext(
        Long userId,
        String capability,
        String explicitModelId,
        String orchestrationModelId,
        Map<String, Object> taskFeatures) {

    /** 能力常量 */
    public static final String CAP_CHAT = "CHAT";
    public static final String CAP_IMAGE_GEN = "IMAGE_GEN";
    public static final String CAP_VIDEO_GEN = "VIDEO_GEN";
    public static final String CAP_SPEECH_ASR = "SPEECH_ASR";
    public static final String CAP_SPEECH_TTS = "SPEECH_TTS";
    public static final String CAP_RERANK = "RERANK";
    public static final String CAP_EMBEDDING = "EMBEDDING";

    /** 任务特征 key 常量 */
    public static final String FEATURE_INPUT_LENGTH = "inputLength";
    public static final String FEATURE_HAS_IMAGE = "hasImage";
    public static final String FEATURE_HAS_VIDEO = "hasVideo";
    public static final String FEATURE_COST_SENSITIVE = "costSensitive";
    public static final String FEATURE_REASONING_REQUIRED = "reasoningRequired";

    /** 简化构造：只有显式 modelId */
    public static CapabilityRoutingContext of(Long userId, String capability, String explicitModelId) {
        return new CapabilityRoutingContext(userId, capability, explicitModelId, null, null);
    }

    /** 简化构造：纯能力路由，无显式指定 */
    public static CapabilityRoutingContext ofCapability(Long userId, String capability) {
        return new CapabilityRoutingContext(userId, capability, null, null, null);
    }
}
