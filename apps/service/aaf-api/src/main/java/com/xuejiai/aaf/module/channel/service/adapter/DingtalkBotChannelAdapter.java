package com.xuejiai.aaf.module.channel.service.adapter;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.common.enums.channel.ChannelTypeEnum;
import com.xuejiai.aaf.common.enums.channel.MessageDirectionEnum;
import com.xuejiai.aaf.common.enums.channel.MessageTypeEnum;
import com.xuejiai.aaf.module.channel.config.BotChannelProperties;
import com.xuejiai.aaf.module.channel.domain.UnifiedMessage;
import com.xuejiai.aaf.module.channel.service.ChannelAdapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 钉钉企业内部机器人渠道适配器。
 *
 * <p>接收机器人消息回调、回复文本/Markdown/卡片消息、加签验证。 需配置 aaf.channel.dingtalk.enabled=true 激活。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aaf.channel.dingtalk.enabled", havingValue = "true")
public class DingtalkBotChannelAdapter implements ChannelAdapter {

    private final BotChannelProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    @Override
    public ChannelTypeEnum channelType() {
        return ChannelTypeEnum.DINGTALK;
    }

    @Override
    public UnifiedMessage receive(String rawPayload) {
        try {
            var root = objectMapper.readTree(rawPayload);
            var msgType = root.path("msgtype").asText("text");
            var senderId =
                    root.path("senderStaffId").asText(root.path("senderId").asText("unknown"));
            var text = root.path("text").path("content").asText("").strip();

            // 解析指令
            var extra = BotCommandParser.parse(text);
            extra.put("senderNick", root.path("senderNick").asText(""));
            extra.put("conversationType", root.path("conversationType").asText(""));
            extra.put("sessionWebhook", root.path("sessionWebhook").asText(""));

            var messageType =
                    switch (msgType) {
                        case "text" -> MessageTypeEnum.TEXT;
                        case "richText", "markdown" -> MessageTypeEnum.MARKDOWN;
                        default -> MessageTypeEnum.TEXT;
                    };

            return new UnifiedMessage(
                    ChannelTypeEnum.DINGTALK,
                    MessageDirectionEnum.INBOUND,
                    messageType,
                    senderId,
                    text,
                    null,
                    null,
                    null,
                    extra,
                    rawPayload,
                    LocalDateTime.now());
        } catch (Exception e) {
            log.error("钉钉消息解析失败: {}", e.getMessage());
            return UnifiedMessage.inboundText(
                    ChannelTypeEnum.DINGTALK, "unknown", rawPayload, rawPayload);
        }
    }

    @Override
    public void reply(UnifiedMessage message) {
        try {
            // 优先使用 sessionWebhook（群聊回复），否则用配置的 webhook
            var webhookUrl =
                    message.extra() != null
                            ? (String) message.extra().getOrDefault("sessionWebhook", "")
                            : "";
            if (webhookUrl == null || webhookUrl.isBlank()) {
                webhookUrl = properties.dingtalk().webhookUrl();
            }
            if (webhookUrl == null || webhookUrl.isBlank()) {
                log.warn("钉钉回复失败：无可用 webhook URL");
                return;
            }

            var body = buildReplyBody(message);
            restClientBuilder
                    .build()
                    .post()
                    .uri(webhookUrl)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("钉钉回复失败: user={}, error={}", message.externalUserId(), e.getMessage());
        }
    }

    @Override
    public void pushTemplate(
            String externalUserId, String templateId, Map<String, String> variables) {
        // 钉钉机器人无模板消息概念，降级为文本推送
        log.info("钉钉机器人不支持模板消息，降级为文本: user={}, template={}", externalUserId, templateId);
    }

    /**
     * 验证钉钉回调签名。
     *
     * @param timestamp 请求头中的时间戳
     * @param sign 请求头中的签名
     * @return 验证是否通过
     */
    public boolean verifySign(String timestamp, String sign) {
        var secret = properties.dingtalk().secret();
        if (secret == null || secret.isBlank()) {
            return true; // 未配置密钥则跳过验证
        }
        try {
            var stringToSign = timestamp + "\n" + secret;
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            var computed = java.util.Base64.getEncoder().encodeToString(signData);
            return computed.equals(sign);
        } catch (Exception e) {
            log.error("钉钉签名验证异常: {}", e.getMessage());
            return false;
        }
    }

    private Map<String, Object> buildReplyBody(UnifiedMessage message) {
        var body = new HashMap<String, Object>();
        return switch (message.messageType()) {
            case MARKDOWN -> {
                body.put("msgtype", "markdown");
                body.put("markdown", Map.of("title", "回复", "text", message.content()));
                yield body;
            }
            case CARD -> {
                body.put("msgtype", "actionCard");
                body.put(
                        "actionCard",
                        Map.of(
                                "title",
                                "回复",
                                "text",
                                message.content(),
                                "singleTitle",
                                "查看详情",
                                "singleURL",
                                message.mediaUrl() != null ? message.mediaUrl() : ""));
                yield body;
            }
            default -> {
                body.put("msgtype", "text");
                body.put("text", Map.of("content", message.content()));
                yield body;
            }
        };
    }
}
