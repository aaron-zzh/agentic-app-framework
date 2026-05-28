package com.xuejiai.aaf.module.system.mail.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 邮件账号响应。
 *
 * @author AaronZZH & Kiro
 */
public record MailAccountVO(
        @Schema(description = "主键 ID") Long id,
        @Schema(description = "账号名称") String name,
        @Schema(description = "SMTP 主机") String host,
        @Schema(description = "SMTP 端口") Integer port,
        @Schema(description = "用户名") String username,
        @Schema(description = "是否启用 SSL") Boolean sslEnabled,
        @Schema(description = "发件人地址") String fromAddress,
        @Schema(description = "状态") Short status) {}
