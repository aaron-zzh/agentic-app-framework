package com.xuejiai.aaf.module.document.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 文档搜索结果 Response VO。 */
@Schema(description = "文档搜索结果")
public record DocSearchResultVO(
        @Schema(description = "文档编号") Long id,
        @Schema(description = "文档标题") String title,
        @Schema(description = "文件路径") String filePath,
        @Schema(description = "匹配片段") String snippet) {}
