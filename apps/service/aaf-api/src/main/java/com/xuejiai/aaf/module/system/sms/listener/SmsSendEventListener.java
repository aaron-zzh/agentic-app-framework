package com.xuejiai.aaf.module.system.sms.listener;

import java.time.LocalDateTime;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.framework.messaging.sms.SmsSendEvent;
import com.xuejiai.aaf.module.system.sms.domain.SmsLog;
import com.xuejiai.aaf.module.system.sms.repository.SmsLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 监听短信发送事件，异步持久化发送日志。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsSendEventListener {

    private final SmsLogRepository smsLogRepository;
    private final ObjectMapper objectMapper;

    @Async
    @EventListener
    public void onSmsSend(SmsSendEvent event) {
        try {
            var smsLog = new SmsLog();
            smsLog.setPhone(event.phone());
            smsLog.setTemplateCode(event.templateCode());
            smsLog.setParams(objectMapper.writeValueAsString(event.params()));
            smsLog.setProvider(event.provider());
            smsLog.setSendStatus(event.success() ? (short) 1 : (short) 2);
            smsLog.setSendTime(event.sendTime());
            smsLog.setApiRequestId(event.apiRequestId());
            smsLog.setApiCode(event.apiCode());
            smsLog.setApiMsg(event.apiMsg());
            smsLog.setCreatedAt(LocalDateTime.now());
            smsLogRepository.save(smsLog);
        } catch (Exception e) {
            log.error("短信日志持久化失败: phone={}", event.phone(), e);
        }
    }
}
