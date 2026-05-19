package com.xuejiai.aaf.framework.engine.knowledge.rag;

import java.util.Map;

/**
 * RAG 检索结果
 */
public record RagSearchResult(
        String content,
        double score,
        String source,
        Map<String, Object> metadata
) {}
