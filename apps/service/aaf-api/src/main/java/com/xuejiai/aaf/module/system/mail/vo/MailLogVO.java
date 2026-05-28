package com.xuejiai.aaf.module.system.mail.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 邮件日志响应。
 *
 * @author AaronZZH & Kiro
 */
public record MailLogVO(
        @Schema(description = "主键 ID") Long id,
        @Schema(description = "模板 ID") Long templateId,
        @Schema(description = "收件人") String toAddress,
        @Schema(description = "主题") String subject,
        @Schema(description = "发送状态") Short sendStatus,
        @Schema(description = "发送时间") LocalDateTime sendTime,
        @Schema(description = "错误信息") String errorMessage) {}
