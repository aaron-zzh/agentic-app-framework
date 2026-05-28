package com.xuejiai.aaf.module.knowledge.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** 知识库信息 Response VO。 */
@Schema(description = "知识库信息")
public record KnowledgeBaseVO(
        @Schema(description = "知识库编号", example = "1") Long id,
        @Schema(description = "知识库名称", example = "产品文档库") String name,
        @Schema(description = "描述") String description,
        @Schema(description = "向量模型名称", example = "text-embedding-v3") String embeddingModel,
        @Schema(description = "分块策略", example = "recursive") String chunkStrategy,
        @Schema(description = "分块大小", example = "512") Integer chunkSize,
        @Schema(description = "分块重叠", example = "64") Integer chunkOverlap,
        @Schema(description = "状态（0=禁用 1=启用）", example = "1") Integer status,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
