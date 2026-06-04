package com.xuejiai.aaf.module.system.auth.vo;

import org.hibernate.validator.constraints.Length;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 账号密码登录请求。
 *
 * @author AaronZZH & Kiro
 */
public record AuthLoginDTO(
        @Schema(description = "登录账号或邮箱", example = "admin")
                @NotBlank(message = "登录账号或邮箱不能为空")
                @Length(min = 4, max = 200, message = "登录账号或邮箱长度为 4-200 位")
                String username,
        @Schema(description = "密码", example = "123456")
                @NotBlank(message = "密码不能为空")
                @Length(min = 4, max = 16, message = "密码长度为 4-16 位")
                String password) {}
