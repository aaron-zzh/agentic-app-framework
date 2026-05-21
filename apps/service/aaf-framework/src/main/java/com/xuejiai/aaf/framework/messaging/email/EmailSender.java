package com.xuejiai.aaf.framework.messaging.email;

import java.util.List;

/** 邮件发送器接口。 */
public interface EmailSender {

    /** 发送 HTML 邮件 */
    void send(String to, String subject, String htmlContent);

    /** 发送带附件的 HTML 邮件 */
    void sendWithAttachment(
            String to, String subject, String htmlContent, List<Attachment> attachments);
}
