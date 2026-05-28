package com.xuejiai.aaf.module.aigc.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.xuejiai.aaf.module.aigc.enums.MediaAssetType;

import io.swagger.v3.oas.annotations.media.Schema;

/** 素材 Response VO。 */
public record MediaAssetVO(
        @Schema(description = "素材 ID", example = "1") Long id,
        @Schema(description = "素材名称", example = "AI生成风景图") String name,
        @Schema(description = "素材类型", example = "IMAGE") MediaAssetType type,
        @Schema(description = "素材文件 URL", example = "https://cdn.example.com/img.png") String url,
        @Schema(description = "缩略图 URL", example = "https://cdn.example.com/img_thumb.png")
                String thumbnailUrl,
        @Schema(description = "文件大小（字节）", example = "1048576") Long size,
        @Schema(description = "宽度（像素）", example = "1024") Integer width,
        @Schema(description = "高度（像素）", example = "1024") Integer height,
        @Schema(description = "时长（秒），音视频素材使用", example = "15.5") BigDecimal duration,
        @Schema(description = "生成参数（JSON）", example = "{\"prompt\":\"风景\"}")
                String generationParams,
        @Schema(description = "标签，逗号分隔", example = "风景,AI生成") String tags,
        @Schema(description = "分类 ID", example = "1") Long categoryId,
        @Schema(description = "用户 ID", example = "100") Long userId,
        @Schema(description = "乐观锁版本号", example = "0") Integer version,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
