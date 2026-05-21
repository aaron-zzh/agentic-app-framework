package com.xuejiai.aaf.module.system.auth.vo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 发送验证码请求。 */
public record SendCodeDTO(
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String email,
        @NotBlank(message = "类型不能为空")
                @Pattern(
                        regexp = "^(register|login|reset)$",
                        message = "类型必须为 register/login/reset")
                String type) {}
