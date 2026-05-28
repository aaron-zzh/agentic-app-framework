package com.xuejiai.aaf.module.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 知识库统计信息 Response VO。 */
@Schema(description = "知识库统计信息")
public record KnowledgeBaseStatsVO(
        @Schema(description = "文档数量") long documentCount,
        @Schema(description = "分块数量") long chunkCount,
        @Schema(description = "向量数量") long embeddingCount) {}
