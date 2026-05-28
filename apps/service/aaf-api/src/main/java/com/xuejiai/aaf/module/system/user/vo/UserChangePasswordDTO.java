package com.xuejiai.aaf.module.system.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求。
 *
 * @author AaronZZH & Kiro
 */
public record UserChangePasswordDTO(
        @NotBlank(message = "旧密码不能为空") @Schema(description = "旧密码") String oldPassword,
        @NotBlank(message = "新密码不能为空") @Size(min = 6, max = 50, message = "新密码长度 6-50")
                String newPassword) {}
