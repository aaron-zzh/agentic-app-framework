package com.xuejiai.aaf.module.customerservice.service;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.xuejiai.aaf.module.customerservice.config.WecomKfProperties;
import com.xuejiai.aaf.module.customerservice.model.dto.SendMsgRequest;
import com.xuejiai.aaf.module.customerservice.model.dto.SyncMsgResponse;

import lombok.extern.slf4j.Slf4j;

/** 企微客服API客户端 */
@Slf4j
@Component
@ConditionalOnProperty(name = "aaf.wecom.kf.enabled", havingValue = "true")
public class WecomKfApiClient {

    private static final String BASE_URL = "https://qyapi.weixin.qq.com";

    private final WecomKfProperties properties;
    private final RestClient restClient;

    private String accessToken;
    private long tokenExpireTime;

    public WecomKfApiClient(WecomKfProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().baseUrl(BASE_URL).build();
    }

    /** 获取access_token（带缓存） */
    public synchronized String getAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return accessToken;
        }
        var result =
                restClient
                        .get()
                        .uri(
                                "/cgi-bin/gettoken?corpid={corpId}&corpsecret={secret}",
                                properties.getCorpId(),
                                properties.getAppSecret())
                        .retrieve()
                        .body(Map.class);
        if (result != null && (int) result.getOrDefault("errcode", -1) == 0) {
            accessToken = (String) result.get("access_token");
            int expiresIn = (int) result.get("expires_in");
            // 提前5分钟过期
            tokenExpireTime = System.currentTimeMillis() + (expiresIn - 300) * 1000L;
        } else {
            log.error("获取access_token失败: {}", result);
        }
        return accessToken;
    }

    /** 拉取消息 */
    public SyncMsgResponse syncMsg(String openKfId, String cursor, String token) {
        var body =
                Map.of(
                        "open_kfid",
                        openKfId,
                        "cursor",
                        cursor != null ? cursor : "",
                        "token",
                        token != null ? token : "",
                        "limit",
                        1000);
        return restClient
                .post()
                .uri("/cgi-bin/kf/sync_msg?access_token={token}", getAccessToken())
                .body(body)
                .retrieve()
                .body(SyncMsgResponse.class);
    }

    /** 发送文本消息 */
    public Map<String, Object> sendTextMsg(String openKfId, String externalUserId, String content) {
        var request =
                new SendMsgRequest(externalUserId, openKfId, "text", Map.of("content", content));
        return restClient
                .post()
                .uri("/cgi-bin/kf/send_msg?access_token={token}", getAccessToken())
                .body(request)
                .retrieve()
                .body(Map.class);
    }
}
