package com.xuejiai.aaf.framework.intelligent.core.model;

/**
 * 通用 AI 能力路由接口，根据上下文决策链解析最终 modelId。
 *
 * <p>决策链（从高到低）：
 *
 * <ol>
 *   <li>显式指定 — 调用方直接传 modelId
 *   <li>用户偏好 — 用户为该能力设置的默认模型（DB：ai_model_preference）
 *   <li>系统默认 — 管理员配置的全局默认（DB：ai_model_preference，scope=SYSTEM）
 *   <li>yaml 兜底 — 配置文件中的默认值
 * </ol>
 *
 * <p>适用于所有 AI 能力：CHAT / IMAGE_GEN / VIDEO_GEN / SPEECH_ASR / SPEECH_TTS / RERANK / EMBEDDING
 */
public interface CapabilityRouter {

    /**
     * 解析 modelId。
     *
     * @param context 路由上下文
     * @return modelId，永不为 null
     */
    String resolve(CapabilityRoutingContext context);
}
