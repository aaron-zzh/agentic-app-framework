package com.xuejiai.aaf.framework.security.oauth;

/** OAuth 第三方用户信息。 */
public record OAuthUserInfo(
        String provider,
        String providerUserId,
        String username,
        String avatar,
        String accessToken,
        String refreshToken,
        long expiresIn) {}
