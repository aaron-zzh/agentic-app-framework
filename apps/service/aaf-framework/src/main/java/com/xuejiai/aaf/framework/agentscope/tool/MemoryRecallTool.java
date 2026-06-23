/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.tool;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.agentscope.runtime.AafContextHolder;
import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingService;
import com.xuejiai.aaf.framework.engine.memory.AtomMemoryEngine;
import com.xuejiai.aaf.framework.engine.memory.MemoryAtom;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 * 长期记忆召回工具——AAF 原生，对接 {@link AtomMemoryEngine#searchByVector}。
 *
 * <p>userId 隔离：从 {@link AafContextHolder#userId()} 取，不会跨用户串号；上下文未设置时拒绝执行。
 *
 * <p>实现：query → {@link EmbeddingService#embed} 转 1536 维向量 → AtomMemoryEngine 检索 → 序列化返回。
 */
public class MemoryRecallTool {

    private static final Logger log = LoggerFactory.getLogger(MemoryRecallTool.class);

    private final AtomMemoryEngine memoryEngine;
    private final EmbeddingService embeddingService;

    public MemoryRecallTool(AtomMemoryEngine memoryEngine, EmbeddingService embeddingService) {
        this.memoryEngine = memoryEngine;
        this.embeddingService = embeddingService;
    }

    @Tool(
            description =
                    "Recall relevant memories about the current user from long-term memory store."
                            + " Returns top-K atoms with their content, scope, and tags.")
    public String recall_memory(
            @ToolParam(name = "query", description = "Recall query (e.g., '用户的写作偏好')") String query,
            @ToolParam(
                            name = "topK",
                            description = "Maximum number of memory atoms to return (default: 5)")
                    int topK) {
        var k = topK > 0 ? Math.min(topK, 20) : 5;
        var userId = AafContextHolder.userId();
        log.info("[recall_memory] query='{}' topK={} userId={}", query, k, userId);

        if (userId == null) {
            return errorJson("无当前用户上下文（forwardedProps.userId 缺失）");
        }
        if (query == null || query.isBlank()) {
            return errorJson("query 不能为空");
        }

        try {
            float[] vec = embeddingService.embed(query.trim());
            List<MemoryAtom> atoms = memoryEngine.searchByVector(userId, vec, k);
            var simplified =
                    atoms.stream()
                            .map(
                                    a ->
                                            java.util.Map.<String, Object>of(
                                                    "id", a.getId().toString(),
                                                    "scope", a.getScope(),
                                                    "content", a.getContent(),
                                                    "weight", a.getWeight(),
                                                    "eventTime",
                                                            a.getEventTime() == null
                                                                    ? null
                                                                    : a.getEventTime().toString(),
                                                    "tags",
                                                            a.getTags() == null
                                                                    ? List.of()
                                                                    : a.getTags()))
                            .collect(Collectors.toList());
            return JsonUtils.toJsonString(java.util.Map.of(
                    "status",
                    "ok",
                    "userId",
                    userId,
                    "query",
                    query,
                    "topK",
                    k,
                    "atoms",
                    simplified));
        } catch (Exception e) {
            log.error("[recall_memory] 检索失败 query='{}' userId={}", query, userId, e);
            return errorJson("检索失败：" + e.getMessage());
        }
    }

    private static String errorJson(String message) {
        return "{\"status\":\"error\",\"message\":\""
                + message.replace("\"", "\\\"").replace("\n", "\\n")
                + "\",\"atoms\":[]}";
    }
}
