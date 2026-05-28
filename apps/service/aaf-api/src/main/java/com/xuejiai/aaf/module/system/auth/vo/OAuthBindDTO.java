package com.xuejiai.aaf.module.system.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * OAuth 绑定请求体。
 *
 * @author AaronZZH & Kiro
 */
public record OAuthBindDTO(@Schema(description = "OAuth 授权码") @NotBlank String code) {}
