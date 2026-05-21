package com.xuejiai.aaf.module.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建知识库请求。 */
@Schema(description = "创建知识库")
public record CreateKnowledgeBaseRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 1000) String description,
        @Size(max = 100) String embeddingModel,
        @Size(max = 50) String chunkStrategy,
        Integer chunkSize,
        Integer chunkOverlap) {}
