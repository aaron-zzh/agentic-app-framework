package com.xuejiai.aaf.framework.security.oauth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

/** 钉钉 OAuth 客户端。 */
@Slf4j
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
        log.debug("钉钉 token 响应: {}", tokenResp);

        String accessToken = (String) tokenResp.get("accessToken");
        String refreshToken = (String) tokenResp.get("refreshToken");
        int expiresIn =
                tokenResp.get("expireIn") != null
                        ? ((Number) tokenResp.get("expireIn")).intValue()
                        : 7200;

        // 获取用户信息
        var userResp =
                restClient
                        .get()
                        .uri(USERINFO_URL)
                        .header("x-acs-dingtalk-access-token", accessToken)
                        .retrieve()
                        .body(Map.class);
        log.debug("钉钉用户信息响应: {}", userResp);

        return new OAuthUserInfo(
                "dingtalk",
                (String) userResp.get("openId"),
                (String) userResp.get("nick"),
                (String) userResp.get("avatarUrl"),
                accessToken,
                refreshToken,
                expiresIn);
    }
}
