package com.xuejiai.aaf.module.system.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 意图识别结果
 *
 * @author AaronZZH & Kiro
 */
public record IntentResult(
        @Schema(description = "意图类型", example = "CHAT") String intent,
        @Schema(description = "置信度", example = "0.9") double confidence,
        @Schema(description = "推荐动作", example = "chat_reply") String action) {}
