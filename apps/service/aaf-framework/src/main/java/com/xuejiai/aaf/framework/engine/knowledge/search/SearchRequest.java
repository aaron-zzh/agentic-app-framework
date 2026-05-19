package com.xuejiai.aaf.framework.engine.knowledge.search;

import java.util.List;

/**
 * 相似度搜索请求参数
 */
public record SearchRequest(
        String query,
        int topK,
        double similarityThreshold,
        Long knowledgeBaseId,
        Long documentId,
        List<String> tags
) {
    public SearchRequest(String query) {
        this(query, 5, 0.7, null, null, null);
    }

    public SearchRequest {
        if (topK <= 0) topK = 5;
        if (similarityThreshold <= 0) similarityThreshold = 0.7;
    }
}
