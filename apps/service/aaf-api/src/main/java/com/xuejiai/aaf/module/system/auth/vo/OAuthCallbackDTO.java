package com.xuejiai.aaf.module.system.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * OAuth 回调请求体。
 *
 * @author AaronZZH & Kiro
 */
public record OAuthCallbackDTO(
        @Schema(description = "OAuth 授权码") @NotBlank String code,
        @Schema(description = "设备 ID") String deviceId,
        @Schema(description = "邀请码（可选，仅 OAuth 首次注册时绑定推荐人）") String referrerCode) {}
