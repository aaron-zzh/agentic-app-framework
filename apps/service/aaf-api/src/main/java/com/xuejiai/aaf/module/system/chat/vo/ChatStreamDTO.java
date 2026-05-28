package com.xuejiai.aaf.module.system.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * AI 对话流式请求
 *
 * @author AaronZZH & Kiro
 */
public record ChatStreamDTO(
        @Schema(description = "消息内容", example = "你好，请介绍一下自己") @NotBlank String content) {}
