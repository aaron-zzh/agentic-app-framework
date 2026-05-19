package com.xuejiai.aaf.module.system.vo;

import jakarta.validation.constraints.NotBlank;

/** OAuth 绑定请求体。 */
public record OAuthBindDTO(@NotBlank String code) {}
