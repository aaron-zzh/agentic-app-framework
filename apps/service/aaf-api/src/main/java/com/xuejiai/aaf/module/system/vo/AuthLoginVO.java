package com.xuejiai.aaf.module.system.vo;

import java.time.LocalDateTime;

/** 登录响应。 */
public record AuthLoginVO(
        Long userId, String accessToken, String refreshToken, LocalDateTime expiresTime) {}
