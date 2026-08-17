package com.xuejiai.aaf.module.channel.service.handler;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.channel.ChannelTypeEnum;
import com.xuejiai.aaf.common.enums.channel.MessageDirectionEnum;
import com.xuejiai.aaf.common.enums.channel.MessageTypeEnum;
import com.xuejiai.aaf.module.ai.assistant.port.ChannelAssistantExecutionPort;
import com.xuejiai.aaf.module.ai.assistant.port.ChannelAssistantExecutionPort.Request;
import com.xuejiai.aaf.module.channel.domain.BotBinding;
import com.xuejiai.aaf.module.channel.domain.UnifiedMessage;
import com.xuejiai.aaf.module.channel.repository.BotBindingRepository;
import com.xuejiai.aaf.module.channel.repository.ChannelPlatformRepository;
import com.xuejiai.aaf.module.channel.service.MessageHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 将通用机器人渠道消息转换为 Assistant 命令。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssistantMessageHandler implements MessageHandler {

    private static final String DEFAULT_FALLBACK = "抱歉，我暂时无法回答这个问题，请稍后再试。";
    private static final Set<ChannelTypeEnum> SUPPORTED_CHANNELS =
            Set.of(ChannelTypeEnum.DINGTALK, ChannelTypeEnum.FEISHU);

    private final ChannelPlatformRepository platformRepository;
    private final BotBindingRepository bindingRepository;
    private final ChannelAssistantExecutionPort assistants;

    @Override
    public boolean supports(UnifiedMessage message) {
        return SUPPORTED_CHANNELS.contains(message.channelType())
                && message.messageType() != MessageTypeEnum.EVENT;
    }

    @Override
    public UnifiedMessage handle(UnifiedMessage message) {
        var binding = resolveBinding(message.channelType());
        if (binding == null) {
            return null;
        }
        if (message.content() == null || message.content().isBlank()) {
            return null;
        }

        try {
            var reply =
                    assistants.execute(
                            new Request(
                                    binding.getOrgId(),
                                    binding.getOwnerId(),
                                    binding.getAssistantId(),
                                    message.channelType().getCode(),
                                    binding.getId().toString(),
                                    message.externalUserId(),
                                    sourceMessageId(message),
                                    message.content(),
                                    Instant.now()));
            return reply(message, reply);
        } catch (RuntimeException failure) {
            log.error(
                    "渠道 Assistant 执行失败: channel={}, bindingId={}, errorType={}",
                    message.channelType().getCode(),
                    binding.getId(),
                    failure.getClass().getSimpleName());
            return reply(message, fallback(binding));
        }
    }

    @Override
    public int order() {
        return 100;
    }

    private BotBinding resolveBinding(ChannelTypeEnum channelType) {
        var platform = platformRepository.findByTypeAndDeletedFalse(channelType).orElse(null);
        if (platform == null || platform.getStatus() != 0) {
            return null;
        }
        var bindings =
                bindingRepository.findByPlatformIdAndStatusAndDeletedFalse(platform.getId(), 0);
        if (bindings.size() == 1) {
            return bindings.getFirst();
        }
        if (bindings.size() > 1) {
            log.error(
                    "渠道存在多个启用的 Assistant 绑定，拒绝猜测: channel={}, platformId={}",
                    channelType.getCode(),
                    platform.getId());
        }
        return null;
    }

    private String sourceMessageId(UnifiedMessage message) {
        if (message.extra() == null) {
            return "";
        }
        var messageId = message.extra().get("messageId");
        return messageId == null ? "" : messageId.toString();
    }

    private String fallback(BotBinding binding) {
        return binding.getFallbackReply() == null || binding.getFallbackReply().isBlank()
                ? DEFAULT_FALLBACK
                : binding.getFallbackReply();
    }

    private UnifiedMessage reply(UnifiedMessage inbound, String content) {
        return new UnifiedMessage(
                inbound.channelType(),
                MessageDirectionEnum.OUTBOUND,
                MessageTypeEnum.TEXT,
                inbound.externalUserId(),
                content,
                null,
                null,
                null,
                inbound.extra(),
                null,
                LocalDateTime.now());
    }
}
