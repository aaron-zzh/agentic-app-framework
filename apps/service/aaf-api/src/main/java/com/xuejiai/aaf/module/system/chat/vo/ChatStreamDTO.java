package com.xuejiai.aaf.module.system.chat.vo;

import jakarta.validation.constraints.NotBlank;

/** AI 对话流式请求。 */
public record ChatStreamDTO(@NotBlank String content) {}
