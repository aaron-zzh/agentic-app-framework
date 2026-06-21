package com.xuejiai.aaf.module.system.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 发送邮箱验证码请求。
 *
 * <p>与 {@link SendSmsCodeDTO}（短信渠道）对称——前者按邮箱载体，后者按手机载体。
 *
 * @author AaronZZH & Kiro
 */
public record SendEmailCodeDTO(
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") @Schema(description = "邮箱")
                String email,
        @NotBlank(message = "类型不能为空")
                @Pattern(
                        regexp = "^(register|login|reset)$",
                        message = "类型必须为 register/login/reset")
                String type) {}
