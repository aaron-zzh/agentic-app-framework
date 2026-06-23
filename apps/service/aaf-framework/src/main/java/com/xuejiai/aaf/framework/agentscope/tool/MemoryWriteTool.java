/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.tool;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

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
 * 写入长期记忆——AAF 原生，对接 {@link AtomMemoryEngine#store}。
 *
 * <p>实现：
 *
 * <ol>
 *   <li>从 {@link AafContextHolder#userId()} 拿当前用户
 *   <li>调 {@link EmbeddingService#embed} 转向量
 *   <li>构造 {@link MemoryAtom}（scope / content / weight=0.5 / eventTime=now / tags 解析逗号分隔）
 *   <li>调 {@link AtomMemoryEngine#store} 持久化
 * </ol>
 */
public class MemoryWriteTool {

    private static final Logger log = LoggerFactory.getLogger(MemoryWriteTool.class);

    /** 允许的 scope 取值（参考 ai_memory_atom.scope COMMENT）。 */
    private static final List<String> ALLOWED_SCOPES =
            List.of("short_term", "long_term", "episodic", "procedural");

    private final AtomMemoryEngine memoryEngine;
    private final EmbeddingService embeddingService;

    public MemoryWriteTool(AtomMemoryEngine memoryEngine, EmbeddingService embeddingService) {
        this.memoryEngine = memoryEngine;
        this.embeddingService = embeddingService;
    }

    @Tool(
            description =
                    "Save a stable, useful piece of information about the current user to"
                            + " long-term memory. Do NOT use this for transient details."
                            + " scope must be one of: short_term, long_term, episodic, procedural.")
    public String write_memory(
            @ToolParam(name = "content", description = "Memory content (concise, fact-like)")
                    String content,
            @ToolParam(
                            name = "scope",
                            description =
                                    "short_term | long_term | episodic | procedural (defaults to"
                                            + " long_term)")
                    String scope,
            @ToolParam(
                            name = "tags",
                            description = "Comma-separated tags (e.g., 'preference,style,brand')")
                    String tags) {
        var userId = AafContextHolder.userId();
        log.info(
                "[write_memory] userId={} scope={} contentLen={}",
                userId,
                scope,
                content == null ? 0 : content.length());

        if (userId == null) {
            return errorJson("无当前用户上下文（forwardedProps.userId 缺失）");
        }
        if (content == null || content.isBlank()) {
            return errorJson("content 不能为空");
        }

        var s = (scope == null || scope.isBlank()) ? "long_term" : scope.trim().toLowerCase();
        if (!ALLOWED_SCOPES.contains(s)) {
            return errorJson("scope 必须是 " + ALLOWED_SCOPES + " 之一，收到: " + s);
        }

        try {
            float[] vec = embeddingService.embed(content.trim());
            var atom = new MemoryAtom();
            atom.setUserId(userId);
            atom.setScope(s);
            atom.setContent(content.trim());
            atom.setEmbedding(vec);
            atom.setEventTime(Instant.now());
            atom.setValidFrom(Instant.now());
            atom.setWeight(0.5);
            atom.setAccessCount(0);
            if (tags != null && !tags.isBlank()) {
                atom.setTags(
                        Arrays.stream(tags.split(","))
                                .map(String::trim)
                                .filter(t -> !t.isBlank())
                                .toList());
            }
            var saved = memoryEngine.store(atom);
            return JsonUtils.toJsonString(
                    java.util.Map.of(
                            "status",
                            "ok",
                            "saved",
                            true,
                            "atomId",
                            saved.getId().toString(),
                            "userId",
                            userId,
                            "scope",
                            s));
        } catch (Exception e) {
            log.error("[write_memory] 写入失败 userId={} scope={}", userId, s, e);
            return errorJson("写入失败：" + e.getMessage());
        }
    }

    private static String errorJson(String message) {
        return "{\"status\":\"error\",\"saved\":false,\"message\":\""
                + message.replace("\"", "\\\"").replace("\n", "\\n")
                + "\"}";
    }
}
