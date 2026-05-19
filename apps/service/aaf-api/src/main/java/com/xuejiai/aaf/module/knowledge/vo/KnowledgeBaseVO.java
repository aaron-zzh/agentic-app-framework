package com.xuejiai.aaf.module.knowledge.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** 知识库响应。 */
@Schema(description = "知识库信息")
public record KnowledgeBaseVO(
        Long id,
        String name,
        String description,
        String embeddingModel,
        String chunkStrategy,
        Integer chunkSize,
        Integer chunkOverlap,
        Integer status,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
