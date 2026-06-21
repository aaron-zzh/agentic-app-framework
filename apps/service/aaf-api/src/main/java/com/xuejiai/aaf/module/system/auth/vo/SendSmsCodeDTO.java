package com.xuejiai.aaf.module.system.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 发送手机验证码请求。
 *
 * @author AaronZZH & Kiro
 */
public record SendSmsCodeDTO(
        @NotBlank(message = "手机号不能为空")
                @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
                @Schema(description = "手机号")
                String phone,
        @NotBlank(message = "类型不能为空")
                @Pattern(
                        regexp = "^(register|login|reset)$",
                        message = "类型必须为 register/login/reset")
                @Schema(description = "验证码类型")
                String type) {}
