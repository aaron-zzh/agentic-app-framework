package com.xuejiai.aaf.framework.engine.knowledge.search;

import java.util.Map;

/**
 * 相似度搜索结果
 */
public record SearchResult(
        String content,
        double score,
        Map<String, Object> metadata,
        String chunkId,
        String documentId
) {}
