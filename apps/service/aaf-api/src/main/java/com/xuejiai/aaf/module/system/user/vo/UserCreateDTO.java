package com.xuejiai.aaf.module.system.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建用户请求
 *
 * @author AaronZZH & Kiro
 */
public record UserCreateDTO(
        @Schema(description = "用户名", example = "zhangsan")
                @NotBlank(message = "用户名不能为空")
                @Size(max = 50, message = "用户名最长 50 字符")
                String username,
        @Schema(description = "密码", example = "123456")
                @NotBlank(message = "密码不能为空")
                @Size(min = 6, max = 50, message = "密码长度 6-50")
                String password,
        @Schema(description = "昵称", example = "张三") @Size(max = 100, message = "昵称最长 100 字符")
                String nickname) {}
