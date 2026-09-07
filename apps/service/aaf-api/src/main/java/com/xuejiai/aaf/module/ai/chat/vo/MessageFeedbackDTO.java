package com.xuejiai.aaf.module.ai.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 消息反馈请求 DTO（点赞/点踩，AAF-114 #11412）。
 *
 * <p>{@code messageId} 不在此 DTO 中——由 URL path 携带（{@code threadId + aguiMessageId}），与 assistant-ui
 * {@code SubmitFeedbackOptions} 官方协议（只有 {@code messageId, type}）对齐后再由 AAF 补充 {@code reason} 等扩展字段；
 * 官方 {@code FeedbackAdapter.submit} 不支持一次性携带原因文本，负反馈原因由前端补充 UI 单独采集后调用同一接口传入。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "消息反馈请求")
public record MessageFeedbackDTO(
        @Schema(
                        description = "反馈类型（positive=正反馈, negative=负反馈）",
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        example = "positive")
                @NotBlank
                String type,
        @Schema(description = "反馈原因（负反馈时前端补充采集，可选）") String reason,
        @Schema(description = "本次回复使用的模型标识（可选，用于问题定位）") String model,
        @Schema(description = "本次回复的 AG-UI runId（可选，用于问题定位）") String runId) {}
