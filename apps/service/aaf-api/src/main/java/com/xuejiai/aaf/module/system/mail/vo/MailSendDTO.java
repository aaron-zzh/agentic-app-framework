package com.xuejiai.aaf.module.system.mail.vo;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 发送邮件请求。
 *
 * @author AaronZZH & Kiro
 */
public record MailSendDTO(
        @Schema(description = "收件人地址") @NotBlank String toAddress,
        @Schema(description = "模板编码") @NotBlank String templateCode,
        @Schema(description = "模板变量") Map<String, String> params) {}
