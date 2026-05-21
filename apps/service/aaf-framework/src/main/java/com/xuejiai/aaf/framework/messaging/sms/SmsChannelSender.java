package com.xuejiai.aaf.framework.messaging.sms;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;

import com.xuejiai.aaf.framework.messaging.ChannelSender;
import com.xuejiai.aaf.framework.messaging.MessageChannel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 短信渠道发送器，桥接 SmsSender 到统一消息服务。 */
@Slf4j
@RequiredArgsConstructor
public class SmsChannelSender implements ChannelSender {

    private static final java.util.regex.Pattern PHONE_PATTERN =
            java.util.regex.Pattern.compile("^1[3-9]\\d{9}$");

    private final SmsSender smsSender;
    private final SmsRateLimiter rateLimiter;
    private final ApplicationEventPublisher eventPublisher;
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
            // 手机号格式校验
            if (!PHONE_PATTERN.matcher(phone).matches()) {
                log.warn("手机号格式不正确，跳过发送: {}", phone);
                continue;
            }
            rateLimiter.check(phone);
            doSend(phone, subject, params);
        }
    }

    private void doSend(String phone, String templateCode, Map<String, String> params) {
        var sendTime = LocalDateTime.now();
        boolean success = false;
        String apiRequestId = null, apiCode = null, apiMsg = null;
        try {
            smsSender.send(phone, templateCode, params);
            success = true;
            apiCode = "OK";
        } catch (Exception e) {
            apiMsg = e.getMessage();
            log.error("短信发送失败: phone={}, template={}", phone, templateCode, e);
        } finally {
            eventPublisher.publishEvent(
                    new SmsSendEvent(
                            phone,
                            templateCode,
                            params,
                            providerName,
                            success,
                            sendTime,
                            apiRequestId,
                            apiCode,
                            apiMsg));
        }
    }
}
