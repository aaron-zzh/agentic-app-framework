package com.xuejiai.aaf.module.system.user.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 重置密码请求（管理员操作）。 */
public record UserResetPasswordDTO(
        @NotBlank(message = "新密码不能为空") @Size(min = 6, max = 50, message = "新密码长度 6-50")
                String password) {}
