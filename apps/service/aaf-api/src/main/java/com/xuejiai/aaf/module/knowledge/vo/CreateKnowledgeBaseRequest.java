package com.xuejiai.aaf.module.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 创建知识库请求。 */
@Schema(description = "创建知识库请求")
public record CreateKnowledgeBaseRequest(
        @Schema(
                        description = "知识库名称",
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        example = "产品文档库")
                @NotBlank
                @Size(max = 200)
                String name,
        @Schema(description = "描述", example = "存放产品相关文档") @Size(max = 1000) String description,
        @Schema(
                        description = "可见性：PRIVATE 私有、ORG 组织、SYSTEM_PUBLIC 系统公共",
                        example = "PRIVATE")
                @Pattern(regexp = "PRIVATE|ORG|SYSTEM_PUBLIC")
                String visibility,
        @Schema(description = "授权范围编码", example = "product") @Size(max = 128) String scopeCode,
        @Schema(description = "向量模型名称", example = "text-embedding-v3") @Size(max = 100)
                String embeddingModel,
        @Schema(description = "分块策略", example = "recursive")
                @Pattern(regexp = "fixed|recursive|semantic")
                String chunkStrategy,
        @Schema(description = "分块大小", example = "512") @Min(1) @Max(100_000) Integer chunkSize,
        @Schema(description = "分块重叠", example = "64") @Min(0) @Max(99_999) Integer chunkOverlap) {}
