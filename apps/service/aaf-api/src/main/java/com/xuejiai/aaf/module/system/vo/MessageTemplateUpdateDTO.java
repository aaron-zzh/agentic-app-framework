package com.xuejiai.aaf.module.system.vo;

/** 消息模板更新 DTO。 */
public record MessageTemplateUpdateDTO(
        String name,
        String channel,
        String subject,
        String content,
        String variables,
        Short status) {}
