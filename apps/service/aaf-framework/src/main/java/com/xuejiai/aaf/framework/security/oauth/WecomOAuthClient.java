package com.xuejiai.aaf.framework.security.oauth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

/** 企业微信 OAuth 客户端。 */
@Slf4j
public class WecomOAuthClient implements OAuthClient {

    private static final String AUTH_URL = "https://login.work.weixin.qq.com/wwlogin/sso/login";
    private static final String TOKEN_URL = "https://qyapi.weixin.qq.com/cgi-bin/gettoken";
    private static final String USERINFO_URL =
            "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo";

    private final OAuthProperties.WecomConfig config;
    private final RestClient restClient;

    public WecomOAuthClient(OAuthProperties.WecomConfig config, RestClient restClient) {
        this.config = config;
        this.restClient = restClient;
    }

    @Override
    public String provider() {
        return "wecom";
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        return AUTH_URL
                + "?login_type=CorpApp"
                + "&appid="
                + config.corpId()
                + "&agentid="
                + config.agentId()
                + "&redirect_uri="
                + URLEncoder.encode(config.redirectUri(), StandardCharsets.UTF_8)
                + "&state="
                + state;
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo exchangeToken(String code) {
        // 获取企业 access_token
        var tokenResp =
                restClient
                        .get()
                        .uri(
                                TOKEN_URL + "?corpid={corpId}&corpsecret={secret}",
                                config.corpId(),
                                config.secret())
                        .retrieve()
                        .body(Map.class);
        log.debug("企业微信 token 响应: {}", tokenResp);

        String accessToken = (String) tokenResp.get("access_token");
        int expiresIn =
                tokenResp.get("expires_in") != null
                        ? ((Number) tokenResp.get("expires_in")).intValue()
                        : 7200;

        // 用 code 获取用户信息
        var userResp =
                restClient
                        .get()
                        .uri(USERINFO_URL + "?access_token={token}&code={code}", accessToken, code)
                        .retrieve()
                        .body(Map.class);
        log.debug("企业微信用户信息响应: {}", userResp);

        String userId = (String) userResp.get("userid");
        if (userId == null) {
            userId = (String) userResp.get("open_userid");
        }

        return new OAuthUserInfo("wecom", userId, userId, null, accessToken, null, expiresIn);
    }
}
