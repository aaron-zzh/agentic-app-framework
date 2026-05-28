package com.xuejiai.aaf.module.system.auth.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录响应。
 *
 * @author AaronZZH & Kiro
 */
public record AuthLoginVO(
        @Schema(description = "用户 ID") Long userId,
        String accessToken,
        String refreshToken,
        LocalDateTime expiresTime) {}
