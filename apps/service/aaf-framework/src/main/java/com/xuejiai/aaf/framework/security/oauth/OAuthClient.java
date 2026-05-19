package com.xuejiai.aaf.framework.security.oauth;

/** OAuth 客户端抽象接口。 */
public interface OAuthClient {

    /** 提供商标识 */
    String provider();

    /** 构建授权 URL */
    String buildAuthorizationUrl(String state);

    /** 用授权码换取用户信息 */
    OAuthUserInfo exchangeToken(String code);
}
