package com.xuejiai.aaf.module.knowledge.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 从文档库导入知识库的请求。 */
@Schema(description = "从文档库导入知识库")
public record KnowledgeDocumentLibraryImportDTO(
        @Schema(description = "文档库文档 ID，最多 20 个") @NotEmpty @Size(max = 20)
                List<@NotNull Long> documentIds) {}
