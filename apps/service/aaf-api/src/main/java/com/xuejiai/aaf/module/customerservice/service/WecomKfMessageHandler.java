package com.xuejiai.aaf.module.customerservice.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.core.assistant.AssistantExecutor;
import com.xuejiai.aaf.framework.intelligent.core.assistant.AssistantExecutor.AssistantResponse;
import com.xuejiai.aaf.module.customerservice.config.WecomKfProperties;
import com.xuejiai.aaf.module.customerservice.model.dto.SyncMsgResponse.MsgItem;
import com.xuejiai.aaf.module.customerservice.repository.WecomKfAccountBindingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 消息处理器：企微客服渠道 → Assistant 实例。 优先从数据库读取绑定关系（前端可配置），降级到 yaml 配置。 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aaf.wecom.kf.enabled", havingValue = "true")
public class WecomKfMessageHandler {

    private final WecomKfApiClient apiClient;
    private final WecomKfProperties properties;
    private final AssistantExecutor assistantExecutor;
    private final WecomKfAccountBindingRepository bindingRepo;

    /** cursor 持久化（生产环境应存 Redis/DB） */
    private final Map<String, String> cursorStore = new ConcurrentHashMap<>();

    /** 处理回调事件：拉取消息并通过 Assistant 回复 */
    public void handleCallback(String openKfId, String token) {
        var cursor = cursorStore.getOrDefault(openKfId, "");
        var response = apiClient.syncMsg(openKfId, cursor, token);

        if (response == null || response.getErrcode() != 0) {
            log.error("拉取消息失败: {}", response);
            return;
        }

        if (response.getNextCursor() != null) {
            cursorStore.put(openKfId, response.getNextCursor());
        }

        if (response.getMsgList() != null) {
            for (var msg : response.getMsgList()) {
                processMessage(msg);
            }
        }

        if (response.getHasMore() == 1) {
            handleCallback(openKfId, null);
        }
    }

    private void processMessage(MsgItem msg) {
        if (msg.getOrigin() != 3 || !"text".equals(msg.getMsgtype())) {
            return;
        }

        var content = (String) msg.getText().get("content");
        if (content == null || content.isBlank()) {
            return;
        }

        log.info("收到客户消息: userId={}, content={}", msg.getExternalUserId(), content);

        var reply = routeToAssistant(msg.getOpenKfId(), msg.getExternalUserId(), content);
        var result = apiClient.sendTextMsg(msg.getOpenKfId(), msg.getExternalUserId(), reply);
        log.info("回复结果: {}", result);
    }

    /** 路由到 Assistant：优先 DB 绑定 → 降级 yaml 配置 */
    private String routeToAssistant(String openKfId, String externalUserId, String userMessage) {
        var assistantId = resolveAssistantId(openKfId);
        if (assistantId == null || assistantId.isBlank()) {
            log.warn("客服账号 {} 未配置 Assistant", openKfId);
            return properties.getFallbackReply();
        }

        var sessionId = "wecom:%s:%s".formatted(openKfId, externalUserId);

        try {
            AssistantResponse response =
                    assistantExecutor.chat(sessionId, assistantId, null, userMessage);
            if (response.success() && response.content() != null) {
                return response.content();
            }
            log.warn("Assistant 回复失败: {}", response.error());
            return properties.getFallbackReply();
        } catch (Exception e) {
            log.error("调用 Assistant 异常: assistantId={}", assistantId, e);
            return properties.getFallbackReply();
        }
    }

    /** 解析 assistantId：DB 绑定优先，降级到 yaml 配置 */
    private String resolveAssistantId(String openKfId) {
        return bindingRepo
                .findByOpenKfIdAndEnabledTrue(openKfId)
                .map(b -> b.getAssistantId() != null ? b.getAssistantId().toString() : null)
                .orElseGet(() -> properties.getAssistantId(openKfId));
    }
}
