package com.xuejiai.aaf.module.system.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 验证码校验请求。
 *
 * @author AaronZZH & Kiro
 */
public record CaptchaVerifyDTO(
        @Schema(description = "验证码 ID") @NotBlank String captchaId,
        @Schema(description = "用户输入的验证码") @NotBlank String code) {}
