package com.xuejiai.aaf.framework.engine.knowledge.search;

import java.util.UUID;

import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.SourceFilters;

/**
 * 已授权单库向量检索请求。
 *
 * @param userId 查询向量化的计费与路由主体（M53：embedding 成本由触发用户承担）；缺失时检索会被拒绝
 */
public record SearchRequest(
        String query,
        int topK,
        double similarityThreshold,
        UUID knowledgeBaseId,
        SourceFilters sourceFilters,
        Long userId) {
    public SearchRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }
        if (knowledgeBaseId == null) {
            throw new IllegalArgumentException("knowledgeBaseId 不能为空");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK 必须大于零");
        }
        if (similarityThreshold < 0 || similarityThreshold > 1) {
            throw new IllegalArgumentException("similarityThreshold 必须在 0 到 1 之间");
        }
        sourceFilters =
                sourceFilters == null
                        ? new SourceFilters(
                                java.util.Set.of(), java.util.Set.of(), java.util.Set.of())
                        : sourceFilters;
    }
}
