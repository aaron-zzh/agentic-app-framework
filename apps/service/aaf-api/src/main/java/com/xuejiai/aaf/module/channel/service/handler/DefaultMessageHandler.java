package com.xuejiai.aaf.module.channel.service.handler;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.channel.MessageTypeEnum;
import com.xuejiai.aaf.module.channel.domain.UnifiedMessage;
import com.xuejiai.aaf.module.channel.service.MessageHandler;

/**
 * 默认消息处理器——兜底回复。
 *
 * <p>优先级最低（order=Integer.MAX_VALUE），当无其他 handler 处理时生效。
 * AAF-076 客服模块接入后，此 handler 将被客服 handler 优先拦截。
 */
@Component
public class DefaultMessageHandler implements MessageHandler {

    @Override
    public UnifiedMessage handle(UnifiedMessage message) {
        if (message.messageType() == MessageTypeEnum.EVENT) {
            // 事件消息不回复
            return null;
        }
        return UnifiedMessage.outboundText(
                message.channelType(),
                message.externalUserId(),
                "您好，已收到您的消息，稍后为您处理。");
    }

    @Override
    public int order() {
        return Integer.MAX_VALUE;
    }
}
