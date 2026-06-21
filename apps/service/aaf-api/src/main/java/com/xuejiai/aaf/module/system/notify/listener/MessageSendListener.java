package com.xuejiai.aaf.module.system.notify.listener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.messaging.ChannelSender;
import com.xuejiai.aaf.framework.messaging.MessageChannel;
import com.xuejiai.aaf.framework.messaging.MessageLogWriter;
import com.xuejiai.aaf.framework.messaging.MessageSendRequestedEvent;
import com.xuejiai.aaf.framework.messaging.ProviderResponse;
import com.xuejiai.aaf.module.system.notify.domain.MessageLog;
import com.xuejiai.aaf.module.system.notify.repository.MessageLogRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息发送监听器。
 *
 * <p>异步监听 {@link MessageSendRequestedEvent}，按渠道路由实际发送，完成后更新 sys_message_log 状态。 同时实现 {@link
 * MessageLogWriter} 供 MessageService 写待发送日志。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageSendListener implements MessageLogWriter {

    private final List<ChannelSender> senderList;
    private final MessageLogRepository logRepository;

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
            var entity = new MessageLog();
            entity.setChannel(channel);
            entity.setTemplateCode(templateCode);
            entity.setRecipients(JsonUtils.toJsonString(recipients));
            entity.setSubject(subject);
            entity.setContent(content);
            entity.setStatus("PENDING");
            return logRepository.save(entity).getId();
        } catch (Exception e) {
            log.error("写待发送日志失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void markResult(
            Long logId, boolean success, String errorMsg, ProviderResponse response) {
        if (logId == null) return;
        logRepository
                .findById(logId)
                .ifPresent(
                        l -> {
                            l.setStatus(success ? "SUCCESS" : "FAILED");
                            l.setSendTime(LocalDateTime.now());
                            if (!success) {
                                l.setErrorMsg(truncate(errorMsg, 500));
                            }
                            if (response != null) {
                                l.setProvider(response.provider());
                                l.setApiRequestId(response.apiRequestId());
                                l.setApiCode(response.apiCode());
                                l.setApiMsg(truncate(response.apiMsg(), 512));
                            }
                            logRepository.save(l);
                        });
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    // ── 异步发送监听 ──────────────────────────────────────

    @Async
    @EventListener
    public void onSendRequested(MessageSendRequestedEvent event) {
        var request = event.request();
        var sender = senderMap.get(request.channel());
        if (sender == null) {
            log.error("不支持的消息渠道: {}", request.channel());
            markResult(
                    event.logId(),
                    false,
                    "不支持的消息渠道: " + request.channel(),
                    ProviderResponse.empty());
            return;
        }
        try {
            var response =
                    sender.send(
                            request.recipients(),
                            event.subject(),
                            event.renderedContent(),
                            request.variables());
            markResult(event.logId(), true, null, response);
            log.info(
                    "消息发送成功: channel={}, recipients={}",
                    request.channel(),
                    request.recipients().size());
        } catch (Exception e) {
            markResult(event.logId(), false, e.getMessage(), ProviderResponse.empty());
            log.error("消息发送失败: channel={}, error={}", request.channel(), e.getMessage(), e);
        }
    }
}
