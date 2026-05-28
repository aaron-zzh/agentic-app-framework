package com.xuejiai.aaf.module.ai.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 意图识别请求
 *
 * @author AaronZZH & Kiro
 */
public record IntentClassifyDTO(
        @Schema(description = "待识别的用户输入文本", example = "帮我查找最近的文档") @NotBlank String text) {}
