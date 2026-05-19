package com.xuejiai.aaf.module.system.vo;

import java.time.LocalDateTime;

/** 消息模板响应 VO。 */
public record MessageTemplateVO(
        Long id,
        String code,
        String name,
        String channel,
        String subject,
        String content,
        String variables,
        Short status,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
