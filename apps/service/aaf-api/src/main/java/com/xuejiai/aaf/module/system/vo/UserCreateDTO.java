package com.xuejiai.aaf.module.system.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建用户请求。 */
public record UserCreateDTO(
        @NotBlank(message = "用户名不能为空") @Size(max = 50, message = "用户名最长 50 字符") String username,
        @NotBlank(message = "密码不能为空") @Size(min = 6, max = 50, message = "密码长度 6-50")
                String password,
        @Size(max = 100, message = "昵称最长 100 字符") String nickname) {}
