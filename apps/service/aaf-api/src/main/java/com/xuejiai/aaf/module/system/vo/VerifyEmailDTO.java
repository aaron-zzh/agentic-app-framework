package com.xuejiai.aaf.module.system.vo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** 邮箱验证请求。 */
public record VerifyEmailDTO(
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String email,
        @NotBlank(message = "验证码不能为空") String code) {}
