package com.xuejiai.aaf.module.elder.rehab.vo;

import com.xuejiai.aaf.common.enums.elder.RehabGuideCategoryEnum;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 康复指导创建请求。 */
@Schema(description = "创建康复指导")
public record RehabGuideCreateDTO(
        @NotBlank @Size(max = 200) @Schema(description = "标题") String title,
        @NotBlank
                @InEnum(value = RehabGuideCategoryEnum.class, message = "康复指导分类必须是 {value}")
                @Schema(description = "分类")
                String category,
        @NotBlank @Size(max = 1000) @Schema(description = "摘要") String summary,
        @NotBlank @Size(max = 1000) @Schema(description = "适用人群说明") String applicableAudience,
        @NotNull @Positive @Schema(description = "关联知识文档 ID") Long knowledgeDocumentId) {}
