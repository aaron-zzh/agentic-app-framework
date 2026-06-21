package com.xuejiai.aaf.framework.intelligent.core;

import java.util.Map;

/**
 * AI 调用用量接口——所有 AI 能力结果类实现此接口，为结算和用量记录提供统一数据源。
 *
 * <p>分两类数据：
 *
 * <ul>
 *   <li>{@link #standardUsage()}：标准化用量（我们自定义的计费结构，按 quotaType 解释）
 *   <li>{@link #rawUsage()}：供应商原始 usage（API 响应原样透传，用于审计和对账）
 * </ul>
 *
 * <p>各结果类按实际情况覆写，不涉及的字段保持默认值（0/null）。
 */
public interface AiUsage {

    /**
     * 标准化用量——按 quotaType 解释：
     *
     * <ul>
     *   <li>TOKEN： {@code {"inputTokens":1200, "outputTokens":800}}
     *   <li>PER_USE： {@code {"count":1}}
     *   <li>PER_SEC： {@code {"duration":32}}
     *   <li>PER_UNIT： {@code {"resolution":"1080p", "count":1}}
     * </ul>
     */
    default Map<String, Object> standardUsage() {
        return Map.of();
    }

    /**
     * 供应商原始 usage 字段（API 响应中的 usage 对象，原样透传）。
     *
     * <p>示例（视频生成）：
     *
     * <pre>
     * {"duration":5, "input_video_duration":0, "output_video_duration":5,
     *  "video_count":1, "SR":720, "ratio":"16:9"}
     * </pre>
     */
    default Map<String, Object> rawUsage() {
        return Map.of();
    }

    // ===== 便捷访问方法（从 standardUsage 取值，避免调用方手动解析 Map） =====

    default long inputTokens() {
        Object v = standardUsage().get("inputTokens");
        return v instanceof Number n ? n.longValue() : 0;
    }

    default long outputTokens() {
        Object v = standardUsage().get("outputTokens");
        return v instanceof Number n ? n.longValue() : 0;
    }

    default Integer duration() {
        Object v = standardUsage().get("duration");
        return v instanceof Number n ? n.intValue() : 0;
    }

    default String unit() {
        Object v = standardUsage().get("resolution");
        return v instanceof String s ? s : null;
    }

    /** 本次调用的单元数（按张/按次等），默认 1。 */
    default int count() {
        Object v = standardUsage().get("count");
        return v instanceof Number n ? Math.max(1, n.intValue()) : 1;
    }

    /** 空用量（结果类未实现 AiUsage 时的兜底）。 */
    static AiUsage empty() {
        return new AiUsage() {};
    }
}
