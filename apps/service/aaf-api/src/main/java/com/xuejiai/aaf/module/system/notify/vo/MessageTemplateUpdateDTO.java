package com.xuejiai.aaf.module.system.notify.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 消息模板更新 DTO。
 *
 * @author AaronZZH & Kiro
 */
public record MessageTemplateUpdateDTO(
        @Schema(description = "名称") String name,
        String channel,
        @Schema(description = "主题") String subject,
        @Schema(description = "内容") String content,
        String variables,
        @Schema(description = "状态") Short status) {}
