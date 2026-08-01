package com.xuejiai.aaf.framework.security.oauth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/** 钉钉 OAuth 客户端。 */
public class DingtalkOAuthClient implements OAuthClient {

    private static final String AUTH_URL = "https://login.dingtalk.com/oauth2/auth";
    private static final String TOKEN_URL = "https://api.dingtalk.com/v1.0/oauth2/userAccessToken";
    private static final String USERINFO_URL = "https://api.dingtalk.com/v1.0/contact/users/me";

    private final OAuthProperties.DingtalkConfig config;
    private final RestClient restClient;

    public DingtalkOAuthClient(OAuthProperties.DingtalkConfig config, RestClient restClient) {
        this.config = config;
        this.restClient = restClient;
    }

    @Override
    public String provider() {
        return "dingtalk";
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        return AUTH_URL
                + "?client_id="
                + config.clientId()
                + "&redirect_uri="
                + URLEncoder.encode(config.redirectUri(), StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=openid"
                + "&state="
                + state
                + "&prompt=consent";
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo exchangeToken(String code) {
        var tokenResp =
                restClient
                        .post()
                        .uri(TOKEN_URL)
                        .body(
                                Map.of(
                                        "clientId",
                                        config.clientId(),
                                        "clientSecret",
                                        config.clientSecret(),
                                        "code",
                                        code,
                                        "grantType",
                                        "authorization_code"))
                        .retrieve()
                        .body(Map.class);
        if (tokenResp == null) {
            throw new IllegalStateException("钉钉 OAuth token 兑换失败: 响应为空");
        }

        String accessToken = (String) tokenResp.get("accessToken");
        if (!StringUtils.hasText(accessToken)) {
            throw new IllegalStateException("钉钉 OAuth token 响应缺少 accessToken");
        }
        String refreshToken = (String) tokenResp.get("refreshToken");
        int expiresIn =
                tokenResp.get("expireIn") != null
                        ? ((Number) tokenResp.get("expireIn")).intValue()
                        : 7200;

        var userResp =
                restClient
                        .get()
                        .uri(USERINFO_URL)
                        .header("x-acs-dingtalk-access-token", accessToken)
                        .retrieve()
                        .body(Map.class);
        if (userResp == null) {
            throw new IllegalStateException("钉钉 OAuth 用户信息获取失败: 响应为空");
        }
        String openId = (String) userResp.get("openId");
        if (!StringUtils.hasText(openId)) {
            throw new IllegalStateException("钉钉 OAuth 用户响应缺少 openId");
        }

        return new OAuthUserInfo(
                "dingtalk",
                openId,
                (String) userResp.get("nick"),
                (String) userResp.get("avatarUrl"),
                accessToken,
                refreshToken,
                expiresIn);
    }
}
