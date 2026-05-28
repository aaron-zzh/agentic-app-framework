package com.xuejiai.aaf.module.system.auth.vo;

import org.hibernate.validator.constraints.Length;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 邮箱注册请求。
 *
 * @author AaronZZH & Kiro
 */
public record RegisterDTO(
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") @Schema(description = "邮箱")
                String email,
        @NotBlank(message = "密码不能为空") @Length(min = 8, max = 32, message = "密码长度为 8-32 位")
                String password,
        String nickname) {}
