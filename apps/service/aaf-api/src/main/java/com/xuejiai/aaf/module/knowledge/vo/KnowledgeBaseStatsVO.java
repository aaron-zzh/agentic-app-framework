package com.xuejiai.aaf.module.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 知识库统计信息。 */
@Schema(description = "知识库统计")
public record KnowledgeBaseStatsVO(long documentCount, long chunkCount, long embeddingCount) {}
