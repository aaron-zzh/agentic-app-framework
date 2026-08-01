package com.xuejiai.aaf.framework.engine.knowledge.graph;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * LLM 抽取出的实体关系三元组。
 *
 * <p>{@code subjectDescription}/{@code objectDescription} 是 LLM 基于当前文本上下文给主语/宾语补充的一句话描述，
 * 用于后续跨文档实体消歧（{@code EntityResolutionService}）时计算语义相似度，不参与图谱展示的核心字段。
 *
 * <p>JSON 字段名用 {@code subjectDesc}/{@code objectDesc} 与 {@link EntityExtractionPrompt} 输出格式对齐， 通过
 * {@link JsonProperty} 映射到更具描述性的 Java 字段名。
 */
public record ExtractedTriple(
        String subject,
        @JsonProperty("subjectDesc") String subjectDescription,
        String predicate,
        String object,
        @JsonProperty("objectDesc") String objectDescription,
        double confidence) {}
