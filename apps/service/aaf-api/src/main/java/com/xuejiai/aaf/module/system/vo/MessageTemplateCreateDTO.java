package com.xuejiai.aaf.module.system.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 消息模板创建 DTO。 */
public record MessageTemplateCreateDTO(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String channel,
        String subject,
        @NotBlank String content,
        String variables,
        @NotNull Short status) {}
