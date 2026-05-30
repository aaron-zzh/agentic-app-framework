package com.xuejiai.aaf.module.channel.service.handler;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuejiai.aaf.common.enums.channel.ChannelTypeEnum;
import com.xuejiai.aaf.common.enums.channel.MessageTypeEnum;
import com.xuejiai.aaf.framework.intelligent.core.assistant.AssistantExecutor;
import com.xuejiai.aaf.framework.intelligent.core.assistant.AssistantExecutor.AssistantResponse;
import com.xuejiai.aaf.module.channel.domain.ChannelConfig;
import com.xuejiai.aaf.module.channel.domain.UnifiedMessage;
import com.xuejiai.aaf.module.channel.repository.ChannelConfigRepository;
import com.xuejiai.aaf.module.channel.service.MessageHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Assistant 消息处理器——机器人渠道收到消息后自动调用绑定的 Assistant 回复。
 *
 * <p>从 channel_config.ext_config 中读取 assistantId 绑定关系。
 * 支持钉钉/飞书/Webhook 等所有机器人类渠道。
 *
 * <p>优先级高于 DefaultMessageHandler，低于业务专属 handler。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssistantMessageHandler implements MessageHandler {

    private static final String FALLBACK_REPLY = "抱歉，我暂时无法回答这个问题，请稍后再试。";

    /** 支持 Assistant 自动回复的渠道类型 */
    private static final Set<ChannelTypeEnum> SUPPORTED_CHANNELS =
            Set.of(ChannelTypeEnum.DINGTALK, ChannelTypeEnum.FEISHU,
                    ChannelTypeEnum.WEBHOOK, ChannelTypeEnum.WECOM_KF);

    private final ChannelConfigRepository configRepository;
    private final AssistantExecutor assistantExecutor;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(UnifiedMessage message) {
        return SUPPORTED_CHANNELS.contains(message.channelType())
                && message.messageType() != MessageTypeEnum.EVENT;
    }

    @Override
    public UnifiedMessage handle(UnifiedMessage message) {
        var assistantId = resolveAssistantId(message.channelType());
        if (assistantId == null) {
            // 未绑定 Assistant，交给下一个 handler
            return null;
        }

        var sessionId = "%s:%s".formatted(
                message.channelType().getCode(), message.externalUserId());
        var userInput = message.content();
        if (userInput == null || userInput.isBlank()) {
            return null;
        }

        log.info("Assistant 处理: channel={}, user={}, assistantId={}",
                message.channelType().getCode(), message.externalUserId(), assistantId);

        try {
            AssistantResponse response =
                    assistantExecutor.chat(sessionId, assistantId, null, userInput);
            if (response.success() && response.content() != null) {
                return UnifiedMessage.outboundText(
                        message.channelType(), message.externalUserId(), response.content());
            }
            log.warn("Assistant 回复失败: {}", response.error());
        } catch (Exception e) {
            log.error("调用 Assistant 异常: assistantId={}", assistantId, e);
        }

        return UnifiedMessage.outboundText(
                message.channelType(), message.externalUserId(), FALLBACK_REPLY);
    }

    @Override
    public int order() {
        // 高于 DefaultMessageHandler(MAX_VALUE)，低于业务专属 handler(0)
        return 100;
    }

    /**
     * 从 channel_config 的 ext_config 中解析 assistantId。
     * ext_config JSON 格式：{"assistantId": "asst_xxx", ...}
     */
    private String resolveAssistantId(ChannelTypeEnum channelType) {
        return configRepository
                .findByChannelTypeAndDeletedFalse(channelType.getCode())
                .map(this::extractAssistantId)
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private String extractAssistantId(ChannelConfig config) {
        if (config.getExtConfig() == null || config.getExtConfig().isBlank()) {
            return null;
        }
        try {
            var ext = objectMapper.readValue(config.getExtConfig(), Map.class);
            var id = ext.get("assistantId");
            return id instanceof String s && !s.isBlank() ? s : null;
        } catch (Exception e) {
            log.warn("解析 ext_config 失败: configId={}", config.getId(), e);
            return null;
        }
    }
}
