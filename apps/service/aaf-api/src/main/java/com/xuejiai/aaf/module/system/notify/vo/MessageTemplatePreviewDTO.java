package com.xuejiai.aaf.module.system.notify.vo;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 消息模板预览请求 DTO。
 *
 * @author AaronZZH & Kiro
 */
public record MessageTemplatePreviewDTO(
        @Schema(description = "模板变量") Map<String, Object> variables) {}
