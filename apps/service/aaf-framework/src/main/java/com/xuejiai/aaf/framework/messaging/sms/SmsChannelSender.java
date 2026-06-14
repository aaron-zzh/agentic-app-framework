package com.xuejiai.aaf.framework.messaging.sms;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.xuejiai.aaf.framework.messaging.ChannelSender;
import com.xuejiai.aaf.framework.messaging.MessageChannel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 短信渠道发送器，桥接 SmsSender 到统一消息服务。日志由 MessageSendListener 统一处理。 */
@Slf4j
@RequiredArgsConstructor
public class SmsChannelSender implements ChannelSender {

    private static final java.util.regex.Pattern PHONE_PATTERN =
            java.util.regex.Pattern.compile("^1[3-9]\\d{9}$");

    private final SmsSender smsSender;
    private final SmsRateLimiter rateLimiter;
    private final String providerName;

    @Override
    public MessageChannel channel() {
        return MessageChannel.SMS;
    }

    @Override
    public void send(
            List<String> recipients,
            String subject,
            String content,
            Map<String, Object> variables) {
        var params =
                variables.entrySet().stream()
                        .collect(
                                Collectors.toMap(
                                        Map.Entry::getKey, e -> String.valueOf(e.getValue())));
        for (var phone : recipients) {
            if (!PHONE_PATTERN.matcher(phone).matches()) {
                log.warn("手机号格式不正确，跳过发送: {}", phone);
                continue;
            }
            rateLimiter.check(phone);
            smsSender.send(phone, subject, params);
            log.info("短信发送成功: phone={}, template={}, provider={}", phone, subject, providerName);
        }
    }
}
