package com.xuejiai.aaf.module.system.notify.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 消息模板响应 VO。
 *
 * @author AaronZZH & Kiro
 */
public record MessageTemplateVO(
        @Schema(description = "主键 ID") Long id,
        @Schema(description = "编码") String code,
        @Schema(description = "名称") String name,
        String channel,
        @Schema(description = "主题") String subject,
        @Schema(description = "内容") String content,
        String variables,
        @Schema(description = "状态") Short status,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
