package com.xuejiai.aaf.module.system.notify.listener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.framework.messaging.ChannelSender;
import com.xuejiai.aaf.framework.messaging.MessageChannel;
import com.xuejiai.aaf.framework.messaging.MessageLogWriter;
import com.xuejiai.aaf.framework.messaging.MessageSendRequestedEvent;
import com.xuejiai.aaf.module.system.notify.domain.MessageLog;
import com.xuejiai.aaf.module.system.notify.repository.MessageLogRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息发送监听器。
 *
 * <p>异步监听 {@link MessageSendRequestedEvent}，按渠道路由实际发送，完成后更新 sys_message_log 状态。 同时实现 {@link
 * MessageLogWriter} 供 MessageServiceImpl 写待发送日志。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageSendListener implements MessageLogWriter {

    private final List<ChannelSender> senderList;
    private final MessageLogRepository logRepository;
    private final ObjectMapper objectMapper;

    private Map<MessageChannel, ChannelSender> senderMap;

    @PostConstruct
    void init() {
        senderMap =
                senderList.stream()
                        .collect(Collectors.toMap(ChannelSender::channel, Function.identity()));
    }

    // ── MessageLogWriter 实现 ──────────────────────────────

    @Override
    public Long createPending(
            String channel,
            String templateCode,
            List<String> recipients,
            String subject,
            String content) {
        try {
            var log = new MessageLog();
            log.setChannel(channel);
            log.setTemplateCode(templateCode);
            log.setRecipients(objectMapper.writeValueAsString(recipients));
            log.setSubject(subject);
            log.setContent(content);
            log.setStatus("PENDING");
            return logRepository.save(log).getId();
        } catch (Exception e) {
            log.error("写待发送日志失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void markSuccess(Long logId) {
        if (logId == null) return;
        logRepository
                .findById(logId)
                .ifPresent(
                        l -> {
                            l.setStatus("SUCCESS");
                            l.setSendTime(LocalDateTime.now());
                            logRepository.save(l);
                        });
    }

    @Override
    public void markFailed(Long logId, String errorMsg) {
        if (logId == null) return;
        logRepository
                .findById(logId)
                .ifPresent(
                        l -> {
                            l.setStatus("FAILED");
                            l.setErrorMsg(
                                    errorMsg != null && errorMsg.length() > 500
                                            ? errorMsg.substring(0, 500)
                                            : errorMsg);
                            l.setSendTime(LocalDateTime.now());
                            logRepository.save(l);
                        });
    }

    // ── 异步发送监听 ──────────────────────────────────────

    @Async
    @EventListener
    public void onSendRequested(MessageSendRequestedEvent event) {
        var request = event.request();
        var sender = senderMap.get(request.channel());
        if (sender == null) {
            log.error("不支持的消息渠道: {}", request.channel());
            markFailed(event.logId(), "不支持的消息渠道: " + request.channel());
            return;
        }
        try {
            sender.send(
                    request.recipients(),
                    event.subject(),
                    event.renderedContent(),
                    request.variables());
            markSuccess(event.logId());
            log.info(
                    "消息发送成功: channel={}, recipients={}",
                    request.channel(),
                    request.recipients().size());
        } catch (Exception e) {
            markFailed(event.logId(), e.getMessage());
            log.error("消息发送失败: channel={}, error={}", request.channel(), e.getMessage(), e);
        }
    }
}
