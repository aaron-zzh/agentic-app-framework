package com.xuejiai.aaf.module.channel.service.handler;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.channel.ChannelTypeEnum;
import com.xuejiai.aaf.common.enums.channel.MessageTypeEnum;
import com.xuejiai.aaf.framework.intelligent.core.assistant.AssistantExecutor;
import com.xuejiai.aaf.framework.intelligent.core.assistant.AssistantExecutor.AssistantResponse;
import com.xuejiai.aaf.module.channel.domain.BotBinding;
import com.xuejiai.aaf.module.channel.domain.UnifiedMessage;
import com.xuejiai.aaf.module.channel.repository.BotBindingRepository;
import com.xuejiai.aaf.module.channel.repository.ChannelPlatformRepository;
import com.xuejiai.aaf.module.channel.service.MessageHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Assistant 消息处理器——机器人渠道收到消息后自动调用绑定的 Assistant 回复。
 *
 * <p>查询链路：ChannelTypeEnum → ChannelPlatform → BotBinding → assistantId → AssistantExecutor。
 * 支持钉钉/飞书/企微客服/Webhook 等所有机器人类渠道。
 *
 * <p>优先级高于 DefaultMessageHandler，低于业务专属 handler。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssistantMessageHandler implements MessageHandler {

    private static final String DEFAULT_FALLBACK = "抱歉，我暂时无法回答这个问题，请稍后再试。";

    /** 支持 Assistant 自动回复的渠道类型 */
    private static final Set<ChannelTypeEnum> SUPPORTED_CHANNELS =
            Set.of(ChannelTypeEnum.DINGTALK, ChannelTypeEnum.FEISHU,
                    ChannelTypeEnum.WEBHOOK, ChannelTypeEnum.WECOM_KF);

    private final ChannelPlatformRepository platformRepository;
    private final BotBindingRepository bindingRepository;
    private final AssistantExecutor assistantExecutor;

    @Override
    public boolean supports(UnifiedMessage message) {
        return SUPPORTED_CHANNELS.contains(message.channelType())
                && message.messageType() != MessageTypeEnum.EVENT;
    }

    @Override
    public UnifiedMessage handle(UnifiedMessage message) {
        var binding = resolveBinding(message.channelType());
        if (binding == null) {
            return null; // 未绑定，交给下一个 handler
        }

        var userInput = message.content();
        if (userInput == null || userInput.isBlank()) {
            return null;
        }

        var sessionId = "%s:%s".formatted(
                message.channelType().getCode(), message.externalUserId());

        log.info("Assistant 处理: channel={}, user={}, assistantId={}",
                message.channelType().getCode(), message.externalUserId(), binding.getAssistantId());

        try {
            AssistantResponse response =
                    assistantExecutor.chat(sessionId, binding.getAssistantId(), null, userInput);
            if (response.success() && response.content() != null) {
                return UnifiedMessage.outboundText(
                        message.channelType(), message.externalUserId(), response.content());
            }
            log.warn("Assistant 回复失败: {}", response.error());
        } catch (Exception e) {
            log.error("调用 Assistant 异常: assistantId={}", binding.getAssistantId(), e);
        }

        var fallback = binding.getFallbackReply() != null
                ? binding.getFallbackReply() : DEFAULT_FALLBACK;
        return UnifiedMessage.outboundText(
                message.channelType(), message.externalUserId(), fallback);
    }

    @Override
    public int order() {
        return 100;
    }

    /** 查找渠道类型对应的第一个启用的 BotBinding */
    private BotBinding resolveBinding(ChannelTypeEnum channelType) {
        var platform = platformRepository.findByTypeAndDeletedFalse(channelType).orElse(null);
        if (platform == null || platform.getStatus() != 0) {
            return null;
        }
        return bindingRepository
                .findFirstByPlatformIdAndStatusAndDeletedFalseOrderByIdAsc(platform.getId(), 0)
                .orElse(null);
    }
}
