package com.xuejiai.aaf.module.system.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 重置密码请求（管理员操作）。
 *
 * @author AaronZZH & Kiro
 */
public record UserResetPasswordDTO(
        @Schema(description = "新密码", example = "123456")
                @NotBlank(message = "新密码不能为空")
                @Size(min = 6, max = 50, message = "新密码长度 6-50")
                String password) {}
