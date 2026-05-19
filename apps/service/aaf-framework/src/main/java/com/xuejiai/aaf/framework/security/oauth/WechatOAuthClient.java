package com.xuejiai.aaf.framework.security.oauth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

/** 微信开放平台 OAuth 客户端。 */
@Slf4j
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
                + "?appid=" + config.appId()
                + "&redirect_uri=" + URLEncoder.encode(config.redirectUri(), StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=snsapi_login"
                + "&state=" + state
                + "#wechat_redirect";
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo exchangeToken(String code) {
        // 用 code 换取 access_token
        var tokenResp = restClient.get()
                .uri(TOKEN_URL + "?appid={appId}&secret={secret}&code={code}&grant_type=authorization_code",
                        config.appId(), config.appSecret(), code)
                .retrieve()
                .body(Map.class);
        log.debug("微信 token 响应: {}", tokenResp);

        String accessToken = (String) tokenResp.get("access_token");
        String openid = (String) tokenResp.get("openid");
        String refreshToken = (String) tokenResp.get("refresh_token");
        int expiresIn = tokenResp.get("expires_in") != null ? ((Number) tokenResp.get("expires_in")).intValue() : 7200;

        // 获取用户信息
        var userResp = restClient.get()
                .uri(USERINFO_URL + "?access_token={token}&openid={openid}", accessToken, openid)
                .retrieve()
                .body(Map.class);
        log.debug("微信用户信息响应: {}", userResp);

        return new OAuthUserInfo(
                "wechat",
                openid,
                (String) userResp.get("nickname"),
                (String) userResp.get("headimgurl"),
                accessToken,
                refreshToken,
                expiresIn);
    }
}
