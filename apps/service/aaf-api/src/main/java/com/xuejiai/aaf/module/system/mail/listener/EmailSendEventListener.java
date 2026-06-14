package com.xuejiai.aaf.module.system.mail.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.messaging.email.EmailSendEvent;
import com.xuejiai.aaf.module.system.mail.domain.MailLog;
import com.xuejiai.aaf.module.system.mail.repository.MailLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 监听邮件发送事件，异步持久化发送日志。
 *
 * <p>仅记录有模板 ID 的发送（通过 MailService 发送的），直接发送无模板时不记录。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSendEventListener {

    private final MailLogRepository mailLogRepository;

    @Async
    @EventListener
    public void onEmailSend(EmailSendEvent event) {
        // 无模板 ID 的直接发送不记录日志（sys_mail_log.template_id NOT NULL 约束）
        if (event.templateId() == null) return;
        try {
            var mailLog = new MailLog();
            mailLog.setTemplateId(event.templateId());
            mailLog.setToAddress(event.to());
            mailLog.setSubject(event.subject());
            mailLog.setContent(event.content());
            mailLog.setSendStatus(event.success() ? (short) 1 : (short) 2);
            mailLog.setSendTime(event.sendTime());
            mailLog.setErrorMessage(event.errorMessage());
            mailLogRepository.save(mailLog);
        } catch (Exception e) {
            log.error("邮件日志持久化失败: to={}", event.to(), e);
        }
    }
}
