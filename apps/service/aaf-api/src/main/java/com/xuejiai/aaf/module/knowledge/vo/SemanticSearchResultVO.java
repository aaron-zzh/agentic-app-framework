package com.xuejiai.aaf.module.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 语义搜索结果 VO。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "语义搜索结果")
public record SemanticSearchResultVO(
        @Schema(description = "段落编号") Long segmentId,
        @Schema(description = "所属文档编号") Long documentId,
        @Schema(description = "段落内容") String content,
        @Schema(description = "相似度分数") Double score) {}
