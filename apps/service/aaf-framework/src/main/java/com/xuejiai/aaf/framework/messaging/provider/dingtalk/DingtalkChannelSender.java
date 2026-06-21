package com.xuejiai.aaf.framework.messaging.provider.dingtalk;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.xuejiai.aaf.framework.messaging.ChannelSender;
import com.xuejiai.aaf.framework.messaging.MessageChannel;
import com.xuejiai.aaf.framework.messaging.ProviderResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * 钉钉机器人渠道发送器。
 *
 * <p>通过自定义机器人 Webhook 发送 Markdown 消息，适合开发/测试环境接收验证码等通知。
 */
@Slf4j
@RequiredArgsConstructor
public class DingtalkChannelSender implements ChannelSender {

    private final DingtalkProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Override
    public MessageChannel channel() {
        return MessageChannel.DINGTALK;
    }

    @Override
    public ProviderResponse send(
            List<String> recipients,
            String subject,
            String content,
            Map<String, Object> variables) {
        try {
            var url = buildUrl();
            var body =
                    objectMapper.writeValueAsString(
                            Map.of(
                                    "msgtype",
                                    "markdown",
                                    "markdown",
                                    Map.of(
                                            "title",
                                            subject != null ? subject : "消息通知",
                                            "text",
                                            content)));
            restClient
                    .post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("钉钉消息发送成功: subject={}", subject);
            return ProviderResponse.of("dingtalk");
        } catch (Exception e) {
            log.error("钉钉消息发送失败: subject={}", subject, e);
            throw new RuntimeException("钉钉消息发送失败", e);
        }
    }

    /** 构建请求 URL，配置了加签密钥时附加签名参数 */
    private String buildUrl() throws Exception {
        var baseUrl = "https://oapi.dingtalk.com/robot/send?access_token=" + properties.apiKey();
        if (properties.secret() == null || properties.secret().isBlank()) {
            return baseUrl;
        }
        long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + properties.secret();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(
                new SecretKeySpec(
                        properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        String sign = java.util.Base64.getEncoder().encodeToString(signData);
        return baseUrl
                + "&timestamp="
                + timestamp
                + "&sign="
                + java.net.URLEncoder.encode(sign, StandardCharsets.UTF_8);
    }
}
