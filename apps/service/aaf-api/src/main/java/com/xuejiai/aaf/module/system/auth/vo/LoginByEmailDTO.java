package com.xuejiai.aaf.module.system.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 邮箱验证码登录请求。
 *
 * <p>与 {@link LoginByPhoneDTO} 对称：邮箱端不会自动注册，邮箱不存在抛 {@code AUTH_LOGIN_BAD_CREDENTIALS}，
 * 由前端引导跳到「邮箱验证码注册」流程。
 *
 * @author AaronZZH & Kiro
 */
public record LoginByEmailDTO(
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") @Schema(description = "邮箱")
                String email,
        @NotBlank(message = "验证码不能为空") @Schema(description = "验证码") String code) {}
