package com.xuejiai.aaf.module.ai.aigc.media.vo;

import java.math.BigDecimal;

import com.xuejiai.aaf.module.ai.aigc.media.enums.MediaAssetType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 从生成结果保存到素材库 Request DTO。 */
public record SaveFromGenerationDTO(
        @Schema(description = "素材名称（为 null 则自动生成）", example = "AI风景图") String name,
        @Schema(description = "素材类型（为 null 则默认 IMAGE）", example = "IMAGE") MediaAssetType type,
        @Schema(description = "生成结果 URL", example = "https://cdn.example.com/img.png") @NotBlank
                String url,
        @Schema(description = "缩略图 URL", example = "https://cdn.example.com/img_thumb.png")
                String thumbnailUrl,
        @Schema(description = "生成参数（JSON）", example = "{\"prompt\":\"风景\"}")
                String generationParams,
        @Schema(description = "宽度（像素）", example = "1024") Integer width,
        @Schema(description = "高度（像素）", example = "1024") Integer height,
        @Schema(description = "时长（秒）", example = "15.5") BigDecimal duration,
        @Schema(description = "所属素材组 ID") Long groupId,
        @Schema(description = "是否 AI 生成，默认 true") boolean aiGenerated,
        @Schema(description = "AI 模型显示名称", example = "豆包图像生成") String modelName,
        @Schema(description = "模型供应商编码", example = "volcengine") String providerCode) {}
