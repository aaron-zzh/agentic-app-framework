package com.xuejiai.aaf.framework.messaging.sms;

import java.time.LocalDateTime;
import java.util.Map;

/** 短信发送结果事件，由 SmsChannelSender 发布，业务层监听持久化日志。 */
public record SmsSendEvent(
        String phone,
        String templateCode,
        Map<String, String> params,
        String provider,
        boolean success,
        LocalDateTime sendTime,
        String apiRequestId,
        String apiCode,
        String apiMsg) {}
