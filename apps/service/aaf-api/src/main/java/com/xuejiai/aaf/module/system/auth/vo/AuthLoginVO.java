package com.xuejiai.aaf.module.system.auth.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录响应。
 *
 * <p>{@code isNewUser} 用于"登录即注册"场景：true 表示本次登录触发了自动注册。
 *
 * @author AaronZZH &amp; Kiro
 */
public record AuthLoginVO(
        @Schema(description = "用户 ID") Long userId,
        String accessToken,
        String refreshToken,
        LocalDateTime expiresTime,
        @Schema(description = "是否为本次自动注册的新用户") Boolean isNewUser) {}
