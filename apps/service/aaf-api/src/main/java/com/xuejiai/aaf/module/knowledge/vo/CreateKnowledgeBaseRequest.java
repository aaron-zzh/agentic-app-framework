package com.xuejiai.aaf.module.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建/更新知识库 Request VO。 */
@Schema(description = "创建/更新知识库请求")
public record CreateKnowledgeBaseRequest(
        @Schema(
                        description = "知识库名称",
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        example = "产品文档库")
                @NotBlank
                @Size(max = 200)
                String name,
        @Schema(description = "描述", example = "存放产品相关文档") @Size(max = 1000) String description,
        @Schema(description = "向量模型名称", example = "text-embedding-v3") @Size(max = 100)
                String embeddingModel,
        @Schema(description = "分块策略", example = "recursive") @Size(max = 50) String chunkStrategy,
        @Schema(description = "分块大小", example = "512") Integer chunkSize,
        @Schema(description = "分块重叠", example = "64") Integer chunkOverlap) {}
