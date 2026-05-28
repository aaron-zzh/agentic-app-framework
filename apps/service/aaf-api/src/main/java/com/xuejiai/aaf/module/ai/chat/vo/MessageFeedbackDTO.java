package com.xuejiai.aaf.module.ai.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 消息反馈请求 DTO（点赞/点踩）。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "消息反馈请求")
public record MessageFeedbackDTO(
        @Schema(
                        description = "反馈类型（LIKE=点赞, DISLIKE=点踩）",
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        example = "LIKE")
                @NotNull
                String type,
        @Schema(description = "反馈备注") String comment) {}
