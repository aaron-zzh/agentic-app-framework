package com.xuejiai.aaf.framework.messaging.email;

import java.util.List;
import java.util.Map;

import com.xuejiai.aaf.framework.messaging.ChannelSender;
import com.xuejiai.aaf.framework.messaging.MessageChannel;
import com.xuejiai.aaf.framework.messaging.ProviderResponse;

import lombok.RequiredArgsConstructor;

/** 邮件渠道发送器，桥接 EmailSender 到统一消息服务。 */
@RequiredArgsConstructor
public class EmailChannelSender implements ChannelSender {

    private final EmailSender emailSender;

    @Override
    public MessageChannel channel() {
        return MessageChannel.EMAIL;
    }

    @Override
    public ProviderResponse send(
            List<String> recipients,
            String subject,
            String content,
            Map<String, Object> variables) {
        for (var to : recipients) {
            emailSender.send(to, subject, content);
        }
        return ProviderResponse.empty();
    }
}
