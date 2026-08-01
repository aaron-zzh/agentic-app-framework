package com.xuejiai.aaf.framework.messaging.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.mail.javamail.JavaMailSender;

import com.xuejiai.aaf.test.BaseMockitoUnitTest;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

class SpringEmailSenderTest extends BaseMockitoUnitTest {

    @Mock private JavaMailSender mailSender;

    @Test
    @DisplayName("Given HTML 邮件和回复地址 When 发送无附件邮件 Then 构造 UTF-8 MIME 并发送")
    void should_send_html_message_with_reply_to_when_no_attachment() throws Exception {
        var message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        var sender =
                new SpringEmailSender(
                        mailSender,
                        new EmailProperties("noreply@example.com", "support@example.com"));

        sender.send("user@example.com", "注册验证码", "<p>验证码：123456</p>");
        message.saveChanges();

        verify(mailSender).send(same(message));
        assertThat(message.getFrom()[0].toString()).isEqualTo("noreply@example.com");
        assertThat(message.getRecipients(Message.RecipientType.TO)[0].toString())
                .isEqualTo("user@example.com");
        assertThat(message.getSubject()).isEqualTo("注册验证码");
        assertThat(message.getReplyTo()[0].toString()).isEqualTo("support@example.com");
        assertThat(message.getContentType())
                .containsIgnoringCase("text/html")
                .containsIgnoringCase("UTF-8");
        assertThat((String) message.getContent()).contains("验证码：123456");
    }

    @Test
    @DisplayName("Given 附件且未配置回复地址 When 发送邮件 Then 构造 Multipart 并保留附件内容")
    void should_send_multipart_message_without_reply_to_when_attachment_present() throws Exception {
        var message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        var sender =
                new SpringEmailSender(mailSender, new EmailProperties("noreply@example.com", null));
        var content = "测试附件".getBytes(StandardCharsets.UTF_8);
        var attachment = new Attachment("report.txt", content, "text/plain");

        sender.sendWithAttachment("user@example.com", "附件邮件", "<p>请查收附件</p>", List.of(attachment));
        message.saveChanges();

        verify(mailSender).send(same(message));
        assertThat(message.getHeader("Reply-To")).isNull();
        assertThat(message.getContentType()).containsIgnoringCase("multipart");
        var multipart = (Multipart) message.getContent();
        assertThat(multipart.getCount()).isEqualTo(2);
        var attachmentPart = multipart.getBodyPart(1);
        assertThat(attachmentPart.getDisposition()).isEqualTo(Part.ATTACHMENT);
        assertThat(attachmentPart.getFileName()).isEqualTo("report.txt");
        assertThat(attachmentPart.getContentType()).containsIgnoringCase("text/plain");
        assertThat(attachmentPart.getInputStream().readAllBytes()).isEqualTo(content);
    }

    @Test
    @DisplayName("Given MIME 构造失败 When 发送邮件 Then 包装异常且不调用邮件发送器")
    void should_wrap_messaging_exception_when_message_cannot_be_prepared() throws Exception {
        var message = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(message);
        doThrow(new MessagingException("invalid from")).when(message).setFrom(any(Address.class));
        var sender =
                new SpringEmailSender(mailSender, new EmailProperties("noreply@example.com", null));

        assertThatThrownBy(() -> sender.send("user@example.com", "注册验证码", "<p>123456</p>"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("邮件发送失败")
                .hasCauseInstanceOf(MessagingException.class);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
