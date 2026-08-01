package com.xuejiai.aaf.module.system.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * OAuth 绑定请求。
 *
 * <p>M31：{@code state} 为必填——必须先调 {@code GET /auth/oauth/{provider}/bind-url} 取绑定授权链接， 回调后连同 code
 * 一起回传该一次性 state；服务端校验其用途为 BIND 且归属当前用户，防止被诱导绑定他人账号。
 */
@Schema(description = "OAuth 绑定请求")
public record OAuthBindDTO(
        @Schema(description = "OAuth 授权码") @NotBlank String code,
        @Schema(description = "服务端签发的一次性绑定 state") @NotBlank String state) {}
