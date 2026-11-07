package com.xuejiai.aaf.module.knowledge.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 从文档库导入知识库的结果。 */
@Schema(description = "从文档库导入知识库的结果")
public record KnowledgeDocumentLibraryImportVO(
        @Schema(description = "本次新建的知识文档") List<KnowledgeDocumentVO> imported,
        @Schema(description = "跳过的文档及原因") List<SkippedDocumentVO> skipped) {

    public KnowledgeDocumentLibraryImportVO {
        imported = List.copyOf(imported);
        skipped = List.copyOf(skipped);
    }

    @Schema(description = "跳过的文档")
    public record SkippedDocumentVO(
            @Schema(description = "文档库文档 ID") Long documentId,
            @Schema(description = "跳过原因") String reason) {}
}
