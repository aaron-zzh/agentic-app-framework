package com.xuejiai.aaf.module.system.mail.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 邮件模板响应。
 *
 * @author AaronZZH & Kiro
 */
public record MailTemplateVO(
        @Schema(description = "主键 ID") Long id,
        @Schema(description = "模板编码") String code,
        @Schema(description = "模板名称") String name,
        @Schema(description = "邮件主题") String subject,
        @Schema(description = "邮件内容") String content,
        @Schema(description = "关联账号 ID") Long accountId,
        @Schema(description = "模板参数 JSON") String params,
        @Schema(description = "状态") Short status) {}
