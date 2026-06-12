package com.xuejiai.aaf.module.ai.aigc.avatar.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** 数字人形象 Response VO。 */
public record AiDigitalAvatarVO(
        @Schema(description = "ID") Long id,
        @Schema(description = "形象名称") String name,
        @Schema(description = "形象图片 URL") String imageUrl,
        @Schema(description = "图片来源素材 ID") Long sourceAssetId,
        @Schema(description = "检测状态：PENDING / PASSED / FAILED") String detectStatus,
        @Schema(description = "检测失败原因") String detectReason,
        @Schema(description = "默认绑定的克隆音色") String defaultVoice,
        @Schema(description = "所属用户 ID") Long userId,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
