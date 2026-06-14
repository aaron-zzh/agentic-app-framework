package com.xuejiai.aaf.framework.messaging.email;

import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 基于 Spring Mail 的邮件发送器。日志记录由 MessageServiceImpl publish EmailSendEvent 统一处理。 */
@Slf4j
@RequiredArgsConstructor
public class SpringEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final EmailProperties properties;

    @Override
    public void send(String to, String subject, String htmlContent) {
        sendWithAttachment(to, subject, htmlContent, List.of());
    }

    @Override
    public void sendWithAttachment(
            String to, String subject, String htmlContent, List<Attachment> attachments) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, !attachments.isEmpty(), "UTF-8");
            helper.setFrom(properties.from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            if (properties.replyTo() != null) {
                helper.setReplyTo(properties.replyTo());
            }
            for (var attachment : attachments) {
                helper.addAttachment(
                        attachment.filename(),
                        new ByteArrayResource(attachment.content()),
                        attachment.contentType());
            }
            mailSender.send(message);
            log.info("邮件发送成功: to={}, subject={}", to, subject);
        } catch (MessagingException e) {
            log.error("邮件发送失败: to={}, subject={}", to, subject, e);
            throw new RuntimeException("邮件发送失败", e);
        }
    }
}
