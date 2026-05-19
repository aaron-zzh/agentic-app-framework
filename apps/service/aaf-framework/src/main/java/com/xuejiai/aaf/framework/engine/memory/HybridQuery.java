/**
 * 混合检索查询参数。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.engine.memory;

import java.time.Instant;
import java.util.List;

/**
 * 混合检索请求：时序 + 语义 + 标签联合查询。
 *
 * @param userId 用户 ID
 * @param queryEmbedding 查询向量（null 则跳过向量检索）
 * @param timeStart 时间范围起始（null 则不限）
 * @param timeEnd 时间范围结束（null 则不限）
 * @param queryTime 查询提及的时间点（用于时间匹配加分）
 * @param tags 标签过滤（null 则不限）
 * @param topK 返回数量
 */
public record HybridQuery(
    Long userId,
    float[] queryEmbedding,
    Instant timeStart,
    Instant timeEnd,
    Instant queryTime,
    List<String> tags,
    int topK
) {
    public HybridQuery {
        if (topK <= 0) topK = 10;
    }
}
