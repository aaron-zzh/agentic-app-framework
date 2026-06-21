package com.xuejiai.aaf.module.system.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 邮箱验证码注册请求（无需密码）。
 *
 * <p>验证码校验通过后即创建用户并自动登录；与 {@link LoginByEmailDTO} 配套使用。
 */
public record RegisterByEmailDTO(
        @NotBlank @Email @Schema(description = "邮箱") String email,
        @NotBlank @Schema(description = "验证码") String code,
        @Schema(description = "昵称") String nickname,
        @Schema(description = "邀请码（可选）") String referrerCode) {}
