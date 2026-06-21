package com.xuejiai.aaf.module.system.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 绑定手机号请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "绑定手机号")
public record BindPhoneDTO(
        @NotBlank(message = "手机号不能为空")
                @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
                @Schema(description = "新手机号")
                String phone,
        @NotBlank(message = "验证码不能为空")
                @Size(min = 6, max = 6, message = "验证码为 6 位")
                @Schema(description = "短信验证码")
                String code) {}
