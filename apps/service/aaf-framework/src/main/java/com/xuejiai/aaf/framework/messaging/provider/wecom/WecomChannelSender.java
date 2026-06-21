package com.xuejiai.aaf.framework.messaging.provider.wecom;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

import com.xuejiai.aaf.framework.integration.wecom.WecomClient;
import com.xuejiai.aaf.framework.messaging.ChannelSender;
import com.xuejiai.aaf.framework.messaging.MessageChannel;
import com.xuejiai.aaf.framework.messaging.ProviderResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 企业微信消息渠道发送器。
 *
 * <p>通过企业应用工作通知 API 发送 Markdown 消息给指定用户。 recipients 为企业微信 userId 列表。
 */
@Slf4j
@RequiredArgsConstructor
@ConditionalOnBean(WecomClient.class)
public class WecomChannelSender implements ChannelSender {

    private final WecomClient wecomClient;

    @Override
    public MessageChannel channel() {
        return MessageChannel.WECOM;
    }

    @Override
    public ProviderResponse send(
            List<String> recipients,
            String subject,
            String content,
            Map<String, Object> variables) {
        wecomClient.sendMarkdown(recipients, content);
        return ProviderResponse.of("wecom");
    }
}
