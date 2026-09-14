package com.xuejiai.aaf.module.document.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 更新文档 Request VO。 */
@Schema(description = "更新文档请求")
public record DocUpdateDTO(
        @Schema(description = "文档标题") String title,
        @Schema(description = "文档内容（Markdown）") String content,
        @Schema(description = "当前版本编号；修改标题或正文时必填") Long expectedCurrentVersionId,
        @Schema(description = "版本修改说明") String changeSummary,
        @Schema(description = "文档类型", example = "spec") String docType) {}
