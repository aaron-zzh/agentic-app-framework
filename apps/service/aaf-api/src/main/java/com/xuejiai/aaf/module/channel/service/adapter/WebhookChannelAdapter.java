package com.xuejiai.aaf.module.channel.service.adapter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.common.enums.channel.ChannelTypeEnum;
import com.xuejiai.aaf.common.enums.channel.MessageDirectionEnum;
import com.xuejiai.aaf.common.enums.channel.MessageTypeEnum;
import com.xuejiai.aaf.module.channel.domain.UnifiedMessage;
import com.xuejiai.aaf.module.channel.service.ChannelAdapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Webhook 渠道适配器——处理入站 Webhook 推送。
 *
 * <p>将外部系统通过 Webhook 推送的事件转换为 UnifiedMessage，走 MessageHandler 链处理。 始终注册（Webhook 是通用能力，不需要条件激活）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookChannelAdapter implements ChannelAdapter {

    private final ObjectMapper objectMapper;

    @Override
    public ChannelTypeEnum channelType() {
        return ChannelTypeEnum.WEBHOOK;
    }

    @Override
    public UnifiedMessage receive(String rawPayload) {
        try {
            var root = objectMapper.readTree(rawPayload);
            var eventType = root.path("event_type").asText("unknown");
            var source = root.path("source").asText("external");
            var content = root.path("data").toString();

            var extra = new HashMap<String, Object>();
            extra.put("eventType", eventType);
            extra.put("source", source);

            return new UnifiedMessage(
                    ChannelTypeEnum.WEBHOOK,
                    MessageDirectionEnum.INBOUND,
                    MessageTypeEnum.EVENT,
                    source,
                    content,
                    null,
                    eventType,
                    null,
                    extra,
                    rawPayload,
                    LocalDateTime.now());
        } catch (Exception e) {
            log.error("Webhook 消息解析失败: {}", e.getMessage());
            return UnifiedMessage.inboundEvent(
                    ChannelTypeEnum.WEBHOOK, "external", "unknown", null, rawPayload);
        }
    }

    @Override
    public void reply(UnifiedMessage message) {
        // Webhook 入站不需要回复（单向接收）
        log.debug("Webhook 渠道不支持回复: {}", message.externalUserId());
    }

    @Override
    public void pushTemplate(
            String externalUserId, String templateId, Map<String, String> variables) {
        // Webhook 不支持模板推送（出站通过 WebhookService.triggerEvent 实现）
        log.debug("Webhook 渠道不支持模板推送");
    }
}
