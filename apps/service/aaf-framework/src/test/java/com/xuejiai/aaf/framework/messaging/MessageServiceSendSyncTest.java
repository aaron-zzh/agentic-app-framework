package com.xuejiai.aaf.framework.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * MessageService 同步发送（{@link MessageService#sendSync}）单元测试。
 *
 * <p>验证点：
 *
 * <ul>
 *   <li>成功路径：调用 ChannelSender 并标记日志为 SUCCESS
 *   <li>失败路径：标记日志 FAILED 并抛 {@link MessageSendException} 透传到上层
 *   <li>不支持渠道：抛 IllegalArgumentException 阻止后续执行
 * </ul>
 *
 * @author AaronZZH &amp; Kiro
 */
class MessageServiceSendSyncTest {

    private ChannelSender emailSender;
    private MessageTemplateProvider templateProvider;
    private MessageLogWriter logWriter;
    private MessageService messageService;

    @BeforeEach
    void setUp() {
        emailSender = mock(ChannelSender.class);
        when(emailSender.channel()).thenReturn(MessageChannel.EMAIL);

        templateProvider = mock(MessageTemplateProvider.class);
        when(templateProvider.findByCode(anyString()))
                .thenReturn(
                        Optional.of(
                                new MessageTemplateProvider.MessageTemplateInfo(
                                        1L,
                                        "test-tpl",
                                        MessageChannel.EMAIL,
                                        "Subject",
                                        "Hello ${code}")));

        logWriter = mock(MessageLogWriter.class);
        when(logWriter.createPending(anyString(), anyString(), anyList(), anyString(), anyString()))
                .thenReturn(99L);

        var eventPublisher = mock(ApplicationEventPublisher.class);
        var templateEngine = new MessageTemplateEngine();

        messageService =
                new MessageService(
                        List.of(emailSender), templateEngine, templateProvider, eventPublisher);
        // logWriter 是字段注入，单测里反射赋值
        try {
            var field = MessageService.class.getDeclaredField("logWriter");
            field.setAccessible(true);
            field.set(messageService, logWriter);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Given ChannelSender 成功 When sendSync Then 日志标记 SUCCESS 并返回 ProviderResponse")
    void should_mark_log_success_and_return_response_when_send_succeeds() {
        // 准备
        var providerResponse = ProviderResponse.of("smtp");
        when(emailSender.send(anyList(), anyString(), anyString(), anyMap()))
                .thenReturn(providerResponse);
        var request =
                new MessageRequest(
                        MessageChannel.EMAIL,
                        "auth.verify_code.login",
                        List.of("u@example.com"),
                        Map.of("code", "123456"),
                        null);

        // 调用
        var result = messageService.sendSync(request);

        // 断言：返回厂商响应 + 日志标记 SUCCESS
        assertThat(result).isEqualTo(providerResponse);
        verify(logWriter).markResult(eq(99L), eq(true), eq(null), eq(providerResponse));
    }

    @Test
    @DisplayName("Given ChannelSender 抛异常 When sendSync Then 日志标记 FAILED 并抛 MessageSendException")
    void should_mark_log_failed_and_throw_when_send_fails() {
        // 准备：sender 抛 SMTP 异常
        when(emailSender.send(anyList(), anyString(), anyString(), anyMap()))
                .thenThrow(new RuntimeException("SMTP connection timeout"));
        var request =
                new MessageRequest(
                        MessageChannel.EMAIL,
                        "auth.verify_code.login",
                        List.of("u@example.com"),
                        Map.of("code", "123456"),
                        null);

        // 调用 + 断言：抛 MessageSendException 透传渠道信息
        assertThatThrownBy(() -> messageService.sendSync(request))
                .isInstanceOf(MessageSendException.class)
                .hasMessageContaining("SMTP connection timeout")
                .extracting("channel")
                .isEqualTo(MessageChannel.EMAIL);

        // 断言：日志标记 FAILED 含错误信息（让上层可定位）
        verify(logWriter)
                .markResult(
                        eq(99L),
                        eq(false),
                        eq("SMTP connection timeout"),
                        any(ProviderResponse.class));
    }

    @Test
    @DisplayName("Given 不支持的渠道 When sendSync Then 抛 IllegalArgumentException 且不写日志/不调用 sender")
    void should_throw_when_channel_not_supported() {
        // 准备：MessageService 只注册了 EMAIL，请求 SMS（无 sender）
        var request =
                new MessageRequest(
                        MessageChannel.SMS,
                        "any-template",
                        List.of("13800138000"),
                        Map.of("code", "123456"),
                        null);

        // 调用 + 断言：抛 IllegalArgumentException
        assertThatThrownBy(() -> messageService.sendSync(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的消息渠道");

        // 断言：未写日志（prepare 阶段就拒绝），未调用 sender.send
        verify(logWriter, never())
                .createPending(anyString(), anyString(), anyList(), anyString(), anyString());
        verify(emailSender, never()).send(anyList(), anyString(), anyString(), anyMap());
    }
}
