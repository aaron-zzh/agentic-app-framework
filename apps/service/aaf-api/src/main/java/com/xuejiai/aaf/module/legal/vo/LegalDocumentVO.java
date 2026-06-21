package com.xuejiai.aaf.module.legal.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 法律文档公开 VO。
 *
 * <p>仅返回展示所必需的字段，不暴露内部审计信息。
 *
 * @author AaronZZH &amp; Kiro
 */
@Schema(description = "法律文档（服务条款 / 隐私政策）")
public record LegalDocumentVO(
        @Schema(description = "文档 ID") Long id,
        @Schema(description = "文档类型：legal-terms / legal-privacy") String type,
        @Schema(description = "标题") String title,
        @Schema(description = "Markdown 内容") String content,
        @Schema(description = "版本号（front_matter.version）") String version,
        @Schema(description = "生效日期（front_matter.effectiveDate，ISO-8601）") String effectiveDate,
        @Schema(description = "最后更新时间") LocalDateTime updateTime) {}
