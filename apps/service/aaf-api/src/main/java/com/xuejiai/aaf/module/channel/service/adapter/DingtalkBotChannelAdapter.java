package com.xuejiai.aaf.module.channel.service.adapter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder;
import com.dingtalk.open.app.api.message.GenericOpenDingTalkEvent;
import com.dingtalk.open.app.api.security.AuthClientCredential;
import com.dingtalk.open.app.stream.protocol.event.EventAckStatus;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.common.enums.channel.ChannelTypeEnum;
import com.xuejiai.aaf.common.enums.channel.MessageDirectionEnum;
import com.xuejiai.aaf.common.enums.channel.MessageTypeEnum;
import com.xuejiai.aaf.module.channel.config.BotChannelProperties;
import com.xuejiai.aaf.module.channel.domain.UnifiedMessage;
import com.xuejiai.aaf.module.channel.service.ChannelAdapter;
import com.xuejiai.aaf.module.channel.service.ChannelMessageRouter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 钉钉企业内部机器人渠道适配器。
 *
 * <p>支持 Stream 模式（推荐，无需公网回调）和 HTTP 回调两种模式。 配置了 appKey+appSecret 时自动启用 Stream 模式，否则走 HTTP 回调。 需配置
 * aaf.channel.dingtalk.enabled=true 激活。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aaf.channel.dingtalk.enabled", havingValue = "true")
public class DingtalkBotChannelAdapter implements ChannelAdapter {

    private final BotChannelProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final ChannelMessageRouter router;

    /** 应用启动后自动启动 Stream 长连接 */
    @EventListener(ApplicationReadyEvent.class)
    public void startStreamClient() {
        var dingtalk = properties.dingtalk();
        if (dingtalk.clientId() == null
                || dingtalk.clientId().isBlank()
                || dingtalk.clientSecret() == null
                || dingtalk.clientSecret().isBlank()) {
            log.warn("钉钉 clientId/clientSecret 未配置，Stream 模式未启动");
            return;
        }
        try {
            OpenDingTalkStreamClientBuilder.custom()
                    .credential(
                            new AuthClientCredential(dingtalk.clientId(), dingtalk.clientSecret()))
                    .registerAllEventListener(
                            (GenericOpenDingTalkEvent event) -> {
                                try {
                                    var eventId = event.getEventId();
                                    var eventType = event.getEventType();
                                    var bizData = event.getData();
                                    log.debug("钉钉事件: id={}, type={}", eventId, eventType);
                                    // 按事件类型分发，后续事件类型增多时可抽为 DingtalkEventDispatcher Bean
                                    // 注入多个 DingtalkEventHandler 实现，按 eventType 路由
                                    switch (eventType != null ? eventType : "") {
                                        // 机器人消息：路由到 AI 对话处理链
                                        case "im_robot_message" -> {
                                            if (bizData != null) {
                                                router.routeInbound(
                                                        ChannelTypeEnum.DINGTALK,
                                                        bizData.toJSONString());
                                            }
                                        }
                                        // TODO: 审批事件、通讯录变更等后续在此扩展
                                        default -> log.debug("暂不处理的钉钉事件类型: {}", eventType);
                                    }
                                    return EventAckStatus.SUCCESS;
                                } catch (Exception e) {
                                    log.error("钉钉事件处理失败: {}", e.getMessage(), e);
                                    return EventAckStatus.LATER;
                                }
                            })
                    .build()
                    .start();
            log.info("钉钉 Stream 模式已启动");
        } catch (Exception e) {
            log.error("钉钉 Stream 客户端启动失败: {}", e.getMessage(), e);
        }
    }

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
