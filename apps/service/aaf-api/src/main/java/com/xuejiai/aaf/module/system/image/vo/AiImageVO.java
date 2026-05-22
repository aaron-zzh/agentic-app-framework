package com.xuejiai.aaf.module.system.image.vo;

import java.time.LocalDateTime;

/** AI 图像生成记录响应 VO。 */
public record AiImageVO(
        Long id,
        Long userId,
        String platform,
        String prompt,
        Integer width,
        Integer height,
        String status,
        String taskId,
        String picUrl,
        String errorMessage,
        String buttons,
        LocalDateTime finishTime,
        LocalDateTime createTime) {}
