package com.xuejiai.aaf.module.system.auth.vo;

import jakarta.validation.constraints.NotBlank;

/** OAuth 回调请求体。 */
public record OAuthCallbackDTO(@NotBlank String code, String deviceId) {}
