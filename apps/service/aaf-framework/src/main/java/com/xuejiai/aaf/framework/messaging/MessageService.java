package com.xuejiai.aaf.framework.messaging;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/** 统一消息发送服务：先写待发送日志，再发布事件由监听器异步执行实际发送。 */
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

    /** 发送单条消息 */
    public void send(MessageRequest request) {
        String content;
        String subject;
        MessageTemplateProvider.MessageTemplateInfo templateInfo = null;

        if (request.content() != null) {
            content = request.content();
            subject = request.subject();
        } else {
            var templateCode = request.templateCode();
            var channelSpecificCode = templateCode + "_" + request.channel().name();
            templateInfo =
                    templateProvider
                            .findByCode(channelSpecificCode)
                            .or(() -> templateProvider.findByCode(templateCode))
                            .orElseThrow(() -> new RuntimeException("消息模板不存在: " + templateCode));
            content = templateEngine.render(templateInfo.content(), request.variables());
            subject = request.subject() != null ? request.subject() : templateInfo.subject();
        }

        var sender = senderMap.get(request.channel());
        if (sender == null) throw new RuntimeException("不支持的消息渠道: " + request.channel());

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

        eventPublisher.publishEvent(
                new MessageSendRequestedEvent(request, templateInfo, content, subject, logId));

        log.debug(
                "消息发送事件已发布: channel={}, recipients={}",
                request.channel(),
                request.recipients().size());
    }

    /** 批量发送消息 */
    public void batchSend(List<MessageRequest> requests) {
        requests.forEach(this::send);
    }
}
