package com.xuejiai.aaf.framework.security.oauth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/** 微信开放平台 OAuth 客户端。 */
public class WechatOAuthClient implements OAuthClient {

    private static final String AUTH_URL = "https://open.weixin.qq.com/connect/qrconnect";
    private static final String TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token";
    private static final String USERINFO_URL = "https://api.weixin.qq.com/sns/userinfo";

    private final OAuthProperties.WechatConfig config;
    private final RestClient restClient;

    public WechatOAuthClient(OAuthProperties.WechatConfig config, RestClient restClient) {
        this.config = config;
        this.restClient = restClient;
    }

    @Override
    public String provider() {
        return "wechat";
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        return AUTH_URL
                + "?appid="
                + config.appId()
                + "&redirect_uri="
                + URLEncoder.encode(config.redirectUri(), StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=snsapi_login"
                + "&state="
                + state
                + "#wechat_redirect";
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo exchangeToken(String code) {
        var tokenResp =
                restClient
                        .get()
                        .uri(
                                TOKEN_URL
                                        + "?appid={appId}&secret={secret}&code={code}&grant_type=authorization_code",
                                config.appId(),
                                config.appSecret(),
                                code)
                        .retrieve()
                        .body(Map.class);
        requireSuccess(tokenResp, "errcode", "errmsg", "微信 OAuth token 兑换失败");

        String accessToken = (String) tokenResp.get("access_token");
        String openid = (String) tokenResp.get("openid");
        if (!StringUtils.hasText(accessToken) || !StringUtils.hasText(openid)) {
            throw new IllegalStateException("微信 OAuth token 响应缺少 access_token 或 openid");
        }
        String refreshToken = (String) tokenResp.get("refresh_token");
        int expiresIn =
                tokenResp.get("expires_in") != null
                        ? ((Number) tokenResp.get("expires_in")).intValue()
                        : 7200;

        var userResp =
                restClient
                        .get()
                        .uri(
                                USERINFO_URL + "?access_token={token}&openid={openid}",
                                accessToken,
                                openid)
                        .retrieve()
                        .body(Map.class);
        requireSuccess(userResp, "errcode", "errmsg", "微信 OAuth 用户信息获取失败");

        return new OAuthUserInfo(
                "wechat",
                openid,
                (String) userResp.get("nickname"),
                (String) userResp.get("headimgurl"),
                accessToken,
                refreshToken,
                expiresIn);
    }

    private static void requireSuccess(
            Map<String, Object> response,
            String errorCodeField,
            String errorMessageField,
            String message) {
        if (response == null) {
            throw new IllegalStateException(message + ": 响应为空");
        }
        var errorCode = response.get(errorCodeField);
        if (errorCode instanceof Number number && number.intValue() != 0) {
            throw new IllegalStateException(
                    message
                            + ": code="
                            + number.intValue()
                            + ", message="
                            + response.get(errorMessageField));
        }
    }
}
