package com.xuejiai.aaf.framework.engine.knowledge.search;

import java.util.UUID;

import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.SourceFilters;

/** 已授权单库向量检索请求。 */
public record SearchRequest(
        String query,
        int topK,
        double similarityThreshold,
        UUID knowledgeBaseId,
        SourceFilters sourceFilters) {
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
