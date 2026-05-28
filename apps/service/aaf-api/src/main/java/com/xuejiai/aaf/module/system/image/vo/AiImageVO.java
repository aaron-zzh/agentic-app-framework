package com.xuejiai.aaf.module.system.image.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AI 图像生成记录响应 VO。
 *
 * @author AaronZZH & Kiro
 */
public record AiImageVO(
        @Schema(description = "主键 ID") Long id,
        @Schema(description = "用户 ID") Long userId,
        String platform,
        @Schema(description = "提示词") String prompt,
        @Schema(description = "宽度") Integer width,
        @Schema(description = "高度") Integer height,
        @Schema(description = "状态") String status,
        @Schema(description = "任务 ID") String taskId,
        String picUrl,
        String errorMessage,
        String buttons,
        LocalDateTime finishTime,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
