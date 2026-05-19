package com.xuejiai.aaf.framework.messaging.sms;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.xuejiai.aaf.framework.messaging.ChannelSender;
import com.xuejiai.aaf.framework.messaging.MessageChannel;

import lombok.RequiredArgsConstructor;

/** 短信渠道发送器，桥接 SmsSender 到统一消息服务。 */
@RequiredArgsConstructor
public class SmsChannelSender implements ChannelSender {

    private final SmsSender smsSender;
    private final SmsRateLimiter rateLimiter;

    @Override
    public MessageChannel channel() {
        return MessageChannel.SMS;
    }

    @Override
    public void send(List<String> recipients, String subject, String content, Map<String, Object> variables) {
        // 将 variables 转为 String 类型的 params
        var params = variables.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
        for (var phone : recipients) {
            rateLimiter.check(phone);
            // 短信使用模板编码直接发送，content 在此场景不使用（厂商模板）
            smsSender.send(phone, subject, params);
        }
    }
}
