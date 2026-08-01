package com.xuejiai.aaf.framework.security.oauth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/** 企业微信 OAuth 客户端。 */
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
        var tokenResp =
                restClient
                        .get()
                        .uri(
                                TOKEN_URL + "?corpid={corpId}&corpsecret={secret}",
                                config.corpId(),
                                config.secret())
                        .retrieve()
                        .body(Map.class);
        requireSuccess(tokenResp, "企业微信 OAuth token 兑换失败");

        String accessToken = (String) tokenResp.get("access_token");
        if (!StringUtils.hasText(accessToken)) {
            throw new IllegalStateException("企业微信 OAuth token 响应缺少 access_token");
        }
        int expiresIn =
                tokenResp.get("expires_in") != null
                        ? ((Number) tokenResp.get("expires_in")).intValue()
                        : 7200;

        var userResp =
                restClient
                        .get()
                        .uri(USERINFO_URL + "?access_token={token}&code={code}", accessToken, code)
                        .retrieve()
                        .body(Map.class);
        requireSuccess(userResp, "企业微信 OAuth 用户信息获取失败");

        String userId = (String) userResp.get("userid");
        if (!StringUtils.hasText(userId)) {
            userId = (String) userResp.get("open_userid");
        }
        if (!StringUtils.hasText(userId)) {
            throw new IllegalStateException("企业微信 OAuth 用户响应缺少 userid 或 open_userid");
        }

        return new OAuthUserInfo("wecom", userId, userId, null, accessToken, null, expiresIn);
    }

    private static void requireSuccess(Map<String, Object> response, String message) {
        if (response == null) {
            throw new IllegalStateException(message + ": 响应为空");
        }
        var errorCode = response.get("errcode");
        if (errorCode instanceof Number number && number.intValue() != 0) {
            throw new IllegalStateException(
                    message
                            + ": code="
                            + number.intValue()
                            + ", message="
                            + response.get("errmsg"));
        }
    }
}
