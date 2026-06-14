package com.xuejiai.aaf.framework.messaging.email;

import java.time.LocalDateTime;

/** 邮件发送结果事件，由 SpringEmailSender 发布，业务层监听持久化日志。 */
public record EmailSendEvent(
        String to,
        String subject,
        String content,
        boolean success,
        LocalDateTime sendTime,
        String errorMessage,
        /** 关联模板 ID，通过模板发送时传入，直接发送时为 null */
        Long templateId) {

    /** 直接发送（无模板）的快捷构造 */
    public EmailSendEvent(
            String to, String subject, String content,
            boolean success, LocalDateTime sendTime, String errorMessage) {
        this(to, subject, content, success, sendTime, errorMessage, null);
    }
}