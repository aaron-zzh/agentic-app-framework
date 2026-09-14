package com.xuejiai.aaf.module.document.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 公开文档阅读 VO。
 *
 * <p>只承载 {@code publishedVersionId} 对应的不可变精确版本正文与标题，不暴露 {@code currentVersionId} 或草稿内容；
 * 避免公开页在已发布正文上叠加尚未发布的编辑草稿。
 *
 * @author AaronZZH &amp; Kiro
 */
@Schema(description = "公开发布文档（只读精确版本）")
public record DocumentPublicVO(
        @Schema(description = "文档 ID") Long id,
        @Schema(description = "已发布的不可变文档版本 ID") Long documentVersionId,
        @Schema(description = "已发布版本的标题") String title,
        @Schema(description = "已发布版本的 Markdown 正文") String content,
        @Schema(description = "文档类型") String docType,
        @Schema(description = "已发布版本创建时间") LocalDateTime updateTime) {}
