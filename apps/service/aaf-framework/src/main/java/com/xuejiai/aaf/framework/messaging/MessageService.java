package com.xuejiai.aaf.framework.messaging;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * 统一消息发送服务。
 *
 * <p>提供两种发送模式：
 *
 * <ul>
 *   <li>{@link #send(MessageRequest)}：异步发送（写日志后发布事件，由 {@code MessageSendListener} 异步执行）。 适用于通知、营销邮件、批量推送等无需即时反馈的场景。
 *   <li>{@link #sendSync(MessageRequest)}：同步发送（直接调用 ChannelSender，失败抛出异常）。 适用于验证码、关键交易通知等需要将发送结果即时反馈给前端的场景。
 * </ul>
 */
@Slf4j
@Service
public class MessageService {

    private final Map<MessageChannel, ChannelSender> senderMap;
    private final MessageTemplateEngine templateEngine;
    private final MessageTemplateProvider templateProvider;
    private final ApplicationEventPublisher eventPublisher;

    /** 可选注入，业务层实现；未注入时跳过日志记录 */
    @Autowired(required = false)
    private MessageLogWriter logWriter;

    public MessageService(
            List<ChannelSender> senders,
            MessageTemplateEngine templateEngine,
            MessageTemplateProvider templateProvider,
            ApplicationEventPublisher eventPublisher) {
        this.senderMap =
                senders.stream()
                        .collect(Collectors.toMap(ChannelSender::channel, Function.identity()));
        this.templateEngine = templateEngine;
        this.templateProvider = templateProvider;
        this.eventPublisher = eventPublisher;
    }

    /** 异步发送单条消息：渲染模板 + 写 PENDING 日志 + 发布事件，立即返回。 */
    public void send(MessageRequest request) {
        var prepared = prepare(request);
        eventPublisher.publishEvent(
                new MessageSendRequestedEvent(
                        request,
                        prepared.templateInfo(),
                        prepared.content(),
                        prepared.subject(),
                        prepared.logId()));
        log.debug(
                "消息发送事件已发布: channel={}, recipients={}",
                request.channel(),
                request.recipients().size());
    }

    /** 批量异步发送 */
    public void batchSend(List<MessageRequest> requests) {
        requests.forEach(this::send);
    }

    /**
     * 同步发送单条消息：渲染模板 + 写 PENDING 日志 + 直接调用 ChannelSender。
     *
     * <p>发送失败时同步更新日志为 FAILED 并抛出 {@link MessageSendException}，由调用方决定如何处理（如释放限频锁、向前端返回业务错误码）。
     *
     * <p>典型用途：验证码发送（必须让前端感知 SMTP/短信厂商失败，否则用户陷入"发了没收到 + 限频不能重试"死循环）。
     */
    public ProviderResponse sendSync(MessageRequest request) {
        var prepared = prepare(request);
        var sender = senderMap.get(request.channel());
        try {
            var response =
                    sender.send(
                            request.recipients(),
                            prepared.subject(),
                            prepared.content(),
                            request.variables());
            if (logWriter != null) {
                logWriter.markResult(prepared.logId(), true, null, response);
            }
            log.info(
                    "消息同步发送成功: channel={}, recipients={}",
                    request.channel(),
                    request.recipients().size());
            return response;
        } catch (Exception e) {
            if (logWriter != null) {
                logWriter.markResult(
                        prepared.logId(), false, e.getMessage(), ProviderResponse.empty());
            }
            log.error(
                    "消息同步发送失败: channel={}, error={}",
                    request.channel(),
                    e.getMessage(),
                    e);
            throw new MessageSendException(
                    "消息发送失败: " + e.getMessage(), request.channel(), e);
        }
    }

    // ── 私有方法 ──────────────────────────────────────

    /** 渲染模板 + 校验渠道 + 写 PENDING 日志。同步/异步路径共用前置逻辑。 */
    private Prepared prepare(MessageRequest request) {
        String content;
        String subject;
        MessageTemplateProvider.MessageTemplateInfo templateInfo = null;

        if (request.content() != null) {
            // 直接发送，不走模板
            content = request.content();
            subject = request.subject();
        } else if (request.channel() == MessageChannel.SMS) {
            // SMS 渠道：subject 承载 templateCode 透传给 SmsChannelSender；不做本地内容渲染
            // 厂商模板表 sys_sms_template 由 SmsChannelSender 内部解析
            if (request.templateCode() == null || request.templateCode().isBlank()) {
                throw new IllegalArgumentException("短信发送缺少模板编码");
            }
            content = null;
            subject = request.templateCode();
        } else {
            var templateCode = request.templateCode();
            var channelSpecificCode = templateCode + "_" + request.channel().name();
            templateInfo =
                    templateProvider
                            .findByCode(channelSpecificCode)
                            .or(() -> templateProvider.findByCode(templateCode))
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "消息模板不存在: " + templateCode));
            content = templateEngine.render(templateInfo.content(), request.variables());
            subject = request.subject() != null ? request.subject() : templateInfo.subject();
        }

        var sender = senderMap.get(request.channel());
        if (sender == null) {
            throw new IllegalArgumentException("不支持的消息渠道: " + request.channel());
        }

        Long logId = null;
        if (logWriter != null) {
            logId =
                    logWriter.createPending(
                            request.channel().name(),
                            request.templateCode(),
                            request.recipients(),
                            subject,
                            content);
        }
        return new Prepared(content, subject, templateInfo, logId);
    }

    /** prepare 阶段产物，承载渲染后内容与日志 ID。 */
    private record Prepared(
            String content,
            String subject,
            MessageTemplateProvider.MessageTemplateInfo templateInfo,
            Long logId) {}
}
