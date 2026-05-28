package com.xuejiai.aaf.module.system.mail.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建邮件模板请求。
 *
 * @author AaronZZH & Kiro
 */
public record MailTemplateCreateDTO(
        @Schema(description = "模板编码") @NotBlank String code,
        @Schema(description = "模板名称") @NotBlank String name,
        @Schema(description = "邮件主题") @NotBlank String subject,
        @Schema(description = "邮件内容") @NotBlank String content,
        @Schema(description = "关联账号 ID") @NotNull Long accountId,
        @Schema(description = "模板参数 JSON") String params) {}
