package com.xuejiai.aaf.framework.messaging;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/** 消息服务实现，根据渠道路由到对应发送器。 */
@Slf4j
@Service
public class MessageServiceImpl implements MessageService {

    private final Map<MessageChannel, ChannelSender> senderMap;
    private final MessageTemplateEngine templateEngine;
    private final MessageTemplateProvider templateProvider;

    public MessageServiceImpl(
            List<ChannelSender> senders,
            MessageTemplateEngine templateEngine,
            MessageTemplateProvider templateProvider) {
        this.senderMap = senders.stream().collect(Collectors.toMap(ChannelSender::channel, Function.identity()));
        this.templateEngine = templateEngine;
        this.templateProvider = templateProvider;
    }

    @Override
    public void send(MessageRequest request) {
        var templateInfo = templateProvider
                .findByCode(request.templateCode())
                .orElseThrow(() -> new RuntimeException("消息模板不存在: " + request.templateCode()));

        var content = templateEngine.render(templateInfo.content(), request.variables());
        var subject = request.subject() != null ? request.subject() : templateInfo.subject();

        var sender = senderMap.get(request.channel());
        if (sender == null) {
            throw new RuntimeException("不支持的消息渠道: " + request.channel());
        }
        sender.send(request.recipients(), subject, content, request.variables());
        log.info("消息发送成功: channel={}, template={}, recipients={}", request.channel(), request.templateCode(),
                request.recipients().size());
    }

    @Override
    public void batchSend(List<MessageRequest> requests) {
        requests.forEach(this::send);
    }
}
