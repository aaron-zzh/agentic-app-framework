package com.xuejiai.aaf.module.aigc.vo;

import java.math.BigDecimal;

import com.xuejiai.aaf.module.aigc.enums.MediaAssetType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 素材创建 Request DTO。 */
public record MediaAssetCreateDTO(
        @Schema(description = "素材名称", example = "AI风景图") @NotBlank String name,
        @Schema(description = "素材类型", example = "IMAGE") @NotNull MediaAssetType type,
        @Schema(description = "素材文件 URL", example = "https://cdn.example.com/img.png") @NotBlank
                String url,
        @Schema(description = "缩略图 URL", example = "https://cdn.example.com/img_thumb.png")
                String thumbnailUrl,
        @Schema(description = "文件大小（字节）", example = "1048576") Long size,
        @Schema(description = "宽度（像素）", example = "1024") Integer width,
        @Schema(description = "高度（像素）", example = "1024") Integer height,
        @Schema(description = "时长（秒）", example = "15.5") BigDecimal duration,
        @Schema(description = "生成参数（JSON）", example = "{\"prompt\":\"风景\"}")
                String generationParams,
        @Schema(description = "标签，逗号分隔", example = "风景,AI生成") String tags,
        @Schema(description = "分类 ID", example = "1") Long categoryId) {}
