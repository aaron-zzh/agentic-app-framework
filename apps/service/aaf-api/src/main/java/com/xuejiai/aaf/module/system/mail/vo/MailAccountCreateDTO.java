package com.xuejiai.aaf.module.system.mail.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建邮件账号请求。
 *
 * @author AaronZZH & Kiro
 */
public record MailAccountCreateDTO(
        @Schema(description = "账号名称") @NotBlank String name,
        @Schema(description = "SMTP 主机") @NotBlank String host,
        @Schema(description = "SMTP 端口") @NotNull Integer port,
        @Schema(description = "用户名") @NotBlank String username,
        @Schema(description = "密码") @NotBlank String password,
        @Schema(description = "是否启用 SSL") Boolean sslEnabled,
        @Schema(description = "发件人地址") @NotBlank String fromAddress) {}
