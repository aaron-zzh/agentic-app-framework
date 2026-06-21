package com.xuejiai.aaf.module.system.auth.vo;

import org.hibernate.validator.constraints.Length;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 手机验证码重置密码请求。
 *
 * @author AaronZZH & Kiro
 */
public record ResetPasswordByPhoneDTO(
        @NotBlank(message = "手机号不能为空")
                @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
                @Schema(description = "手机号")
                String phone,
        @NotBlank(message = "验证码不能为空")
                @Size(min = 6, max = 6, message = "验证码为 6 位")
                @Schema(description = "验证码")
                String code,
        @NotBlank(message = "新密码不能为空")
                @Length(min = 8, max = 32, message = "密码长度为 8-32 位")
                @Schema(description = "新密码")
                String newPassword) {}
