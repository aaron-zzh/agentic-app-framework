/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.tool;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.agentscope.runtime.AafContextHolder;
import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingService;
import com.xuejiai.aaf.framework.engine.knowledge.search.SearchRequest;
import com.xuejiai.aaf.framework.engine.knowledge.search.SearchResult;
import com.xuejiai.aaf.framework.engine.knowledge.search.SimilaritySearchService;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 * 知识库语义检索工具——AAF 原生，对接 {@link SimilaritySearchService}。
 *
 * <p>实现：
 *
 * <ol>
 *   <li>从 {@link AafContextHolder#knowledgeBaseId()} 获取当前 thread 绑定的知识库（来自
 *       ai_assistant.knowledge_base_id 或 AG-UI {@code forwardedProps.knowledgeBaseId}）
 *   <li>构造 {@link SearchRequest}，由 {@code SimilaritySearchService} 走 pgvector hnsw 检索
 *   <li>结果序列化为 JSON 数组返回给模型
 * </ol>
 */
public class KnowledgeSearchTool {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSearchTool.class);

    private final SimilaritySearchService searchService;
    private final EmbeddingService embeddingService;

    public KnowledgeSearchTool(
            SimilaritySearchService searchService, EmbeddingService embeddingService) {
        this.searchService = searchService;
        this.embeddingService = embeddingService;
    }

    @Tool(
            description =
                    "Search the bound knowledge base for relevant content using semantic search."
                            + " Returns top-K segments with citations.")
    public String search_kb(
            @ToolParam(name = "query", description = "Search query (Chinese or English)")
                    String query,
            @ToolParam(
                            name = "topK",
                            description =
                                    "Maximum number of segments to return (default: 5, max: 20)")
                    int topK) {
        var k = topK > 0 ? Math.min(topK, 20) : 5;
        var kbId = AafContextHolder.knowledgeBaseId();
        log.info("[search_kb] query='{}' topK={} kbId={}", query, k, kbId);

        if (kbId == null) {
            log.debug("[search_kb] 未绑定知识库，跳过检索");
            return "{\"status\":\"ok\",\"kbId\":null,\"query\":\""
                    + query.replace("\"", "\\\"")
                    + "\",\"topK\":"
                    + k
                    + ",\"results\":[]}";
        }
        if (query == null || query.isBlank()) {
            return errorJson("query 不能为空");
        }

        try {
            var request =
                    new SearchRequest(
                            query.trim(),
                            k,
                            0.0, // 不在工具层硬卡阈值；给模型看候选项
                            kbId,
                            null,
                            null);
            List<SearchResult> results = searchService.search(request);
            return JsonUtils.toJsonString(
                    java.util.Map.of(
                            "status", "ok",
                            "kbId", kbId,
                            "query", query,
                            "topK", k,
                            "results", results));
        } catch (Exception e) {
            log.error("[search_kb] 检索或序列化失败 query='{}' kbId={}", query, kbId, e);
            return errorJson("检索失败：" + e.getMessage());
        }
    }

    private static String errorJson(String message) {
        return "{\"status\":\"error\",\"message\":\""
                + message.replace("\"", "\\\"").replace("\n", "\\n")
                + "\",\"results\":[]}";
    }
}
