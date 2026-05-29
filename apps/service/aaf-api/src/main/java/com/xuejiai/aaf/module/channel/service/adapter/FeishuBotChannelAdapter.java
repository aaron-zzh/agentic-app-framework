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
 * 飞书自建应用机器人渠道适配器。
 *
 * <p>接收事件订阅回调、消息发送（文本/Markdown/卡片）、签名验证。
 * 需配置 aaf.channel.feishu.enabled=true 激活。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aaf.channel.feishu.enabled", havingValue = "true")
public class FeishuBotChannelAdapter implements ChannelAdapter {

    private static final String SEND_MSG_URL = "https://open.feishu.cn/open-apis/im/v1/messages";

    private final BotChannelProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    @Override
    public ChannelTypeEnum channelType() {
        return ChannelTypeEnum.FEISHU;
    }

    @Override
    public UnifiedMessage receive(String rawPayload) {
        try {
            var root = objectMapper.readTree(rawPayload);

            // 飞书 URL 验证（challenge 机制）
            if (root.has("challenge")) {
                var extra = new HashMap<String, Object>();
                extra.put("challenge", root.path("challenge").asText());
                extra.put("isChallenge", true);
                return new UnifiedMessage(
                        ChannelTypeEnum.FEISHU,
                        MessageDirectionEnum.INBOUND,
                        MessageTypeEnum.EVENT,
                        "system",
                        null, null,
                        "url_verification", null,
                        extra, rawPayload, LocalDateTime.now());
            }

            // 事件订阅消息
            var event = root.path("event");
            var msgNode = event.path("message");
            var senderId = event.path("sender").path("sender_id").path("open_id").asText("unknown");
            var msgType = msgNode.path("message_type").asText("text");
            var chatId = msgNode.path("chat_id").asText("");

            // 解析消息内容
            var contentStr = msgNode.path("content").asText("{}");
            var contentNode = objectMapper.readTree(contentStr);
            var text = contentNode.path("text").asText("").strip();

            // 去掉 @机器人 的前缀
            if (text.contains("@_all") || text.startsWith("@")) {
                text = text.replaceAll("@\\S+\\s*", "").strip();
            }

            // 解析指令
            var extra = BotCommandParser.parse(text);
            extra.put("chatId", chatId);
            extra.put("messageId", msgNode.path("message_id").asText(""));
            extra.put("chatType", msgNode.path("chat_type").asText(""));

            var messageType = switch (msgType) {
                case "text" -> MessageTypeEnum.TEXT;
                case "post" -> MessageTypeEnum.MARKDOWN;
                case "interactive" -> MessageTypeEnum.CARD;
                default -> MessageTypeEnum.TEXT;
            };

            return new UnifiedMessage(
                    ChannelTypeEnum.FEISHU,
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
            log.error("飞书消息解析失败: {}", e.getMessage());
            return UnifiedMessage.inboundText(
                    ChannelTypeEnum.FEISHU, "unknown", rawPayload, rawPayload);
        }
    }

    @Override
    public void reply(UnifiedMessage message) {
        try {
            var chatId = message.extra() != null
                    ? (String) message.extra().getOrDefault("chatId", "")
                    : "";
            if (chatId == null || chatId.isBlank()) {
                log.warn("飞书回复失败：无 chatId");
                return;
            }

            var token = obtainTenantAccessToken();
            var body = buildSendBody(message, chatId);

            restClientBuilder.build()
                    .post()
                    .uri(SEND_MSG_URL + "?receive_id_type=chat_id")
                    .header("Authorization", "Bearer " + token)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("飞书回复失败: user={}, error={}",
                    message.externalUserId(), e.getMessage());
        }
    }

    @Override
    public void pushTemplate(
            String externalUserId, String templateId, Map<String, String> variables) {
        // 飞书通过卡片消息实现模板推送
        log.info("飞书模板推送（卡片）: user={}, template={}", externalUserId, templateId);
    }

    /**
     * 验证飞书事件回调签名。
     *
     * @param timestamp 请求头中的时间戳
     * @param nonce 请求头中的随机数
     * @param body 请求体
     * @param signature 请求头中的签名
     * @return 验证是否通过
     */
    public boolean verifySign(String timestamp, String nonce, String body, String signature) {
        var token = properties.feishu().verificationToken();
        if (token == null || token.isBlank()) {
            return true;
        }
        try {
            var content = timestamp + nonce + properties.feishu().encryptKey() + body;
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    "".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var hash = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            var computed = bytesToHex(hash);
            return computed.equals(signature);
        } catch (Exception e) {
            log.error("飞书签名验证异常: {}", e.getMessage());
            return false;
        }
    }

    /** 获取 tenant_access_token */
    private String obtainTenantAccessToken() {
        var body = Map.of(
                "app_id", properties.feishu().appId(),
                "app_secret", properties.feishu().appSecret());
        var resp = restClientBuilder.build()
                .post()
                .uri("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")
                .body(body)
                .retrieve()
                .body(Map.class);
        return resp != null ? (String) resp.get("tenant_access_token") : "";
    }

    private Map<String, Object> buildSendBody(UnifiedMessage message, String chatId) {
        var body = new HashMap<String, Object>();
        body.put("receive_id", chatId);
        return switch (message.messageType()) {
            case MARKDOWN -> {
                body.put("msg_type", "post");
                body.put("content", """
                        {"zh_cn":{"title":"回复","content":[[{"tag":"text","text":"%s"}]]}}"""
                        .formatted(message.content()));
                yield body;
            }
            case CARD -> {
                body.put("msg_type", "interactive");
                body.put("content", message.content()); // 卡片 JSON 由调用方构建
                yield body;
            }
            default -> {
                body.put("msg_type", "text");
                body.put("content", """
                        {"text":"%s"}""".formatted(message.content()));
                yield body;
            }
        };
    }

    private static String bytesToHex(byte[] bytes) {
        var sb = new StringBuilder();
        for (var b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
