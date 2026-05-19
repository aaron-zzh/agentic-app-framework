package com.xuejiai.aaf.framework.messaging.internal;

import java.util.List;
import java.util.Map;

import com.xuejiai.aaf.framework.messaging.ChannelSender;
import com.xuejiai.aaf.framework.messaging.MessageChannel;

import lombok.RequiredArgsConstructor;

/** 站内信渠道发送器，桥接 InternalMessageSender 到统一消息服务。 */
@RequiredArgsConstructor
public class InternalChannelSender implements ChannelSender {

    private final InternalMessageSender internalMessageSender;

    @Override
    public MessageChannel channel() {
        return MessageChannel.INTERNAL;
    }

    @Override
    public void send(List<String> recipients, String subject, String content, Map<String, Object> variables) {
        for (var recipient : recipients) {
            var userId = Long.valueOf(recipient);
            internalMessageSender.send(userId, subject, content);
        }
    }
}
