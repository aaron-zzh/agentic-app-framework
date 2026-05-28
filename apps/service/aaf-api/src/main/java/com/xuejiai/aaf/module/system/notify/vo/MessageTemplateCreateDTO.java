package com.xuejiai.aaf.module.system.notify.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 消息模板创建 DTO。
 *
 * @author AaronZZH & Kiro
 */
public record MessageTemplateCreateDTO(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String channel,
        @Schema(description = "主题") String subject,
        @NotBlank String content,
        String variables,
        @NotNull Short status) {}
