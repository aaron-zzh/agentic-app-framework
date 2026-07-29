package com.xuejiai.aaf.module.customerservice.service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.module.ai.assistant.port.ChannelAssistantExecutionPort;
import com.xuejiai.aaf.module.ai.assistant.port.ChannelAssistantExecutionPort.Request;
import com.xuejiai.aaf.module.customerservice.config.WecomKfProperties;
import com.xuejiai.aaf.module.customerservice.model.dto.SyncMsgResponse.MsgItem;
import com.xuejiai.aaf.module.customerservice.model.entity.WecomKfAccountBinding;
import com.xuejiai.aaf.module.customerservice.repository.WecomKfAccountBindingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 消息处理器：企微客服协议接入后统一调用 Assistant v2。 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aaf.wecom.kf.enabled", havingValue = "true")
public class WecomKfMessageHandler {

    private final WecomKfApiClient apiClient;
    private final WecomKfProperties properties;
    private final ChannelAssistantExecutionPort assistants;
    private final WecomKfAccountBindingRepository bindingRepo;

    /** cursor 持久化（生产环境应存 Redis/DB） */
    private final Map<String, String> cursorStore = new ConcurrentHashMap<>();

    /** 处理回调事件：拉取消息并通过 Assistant 回复 */
    public void handleCallback(String openKfId, String token) {
        var cursor = cursorStore.getOrDefault(openKfId, "");
        var response = apiClient.syncMsg(openKfId, cursor, token);

        if (response == null || response.getErrcode() != 0) {
            log.error("拉取企微客服消息失败: openKfId={}, hasResponse={}", openKfId, response != null);
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

        var reply = routeToAssistant(msg, content);
        var result = apiClient.sendTextMsg(msg.getOpenKfId(), msg.getExternalUserId(), reply);
        log.info("企微客服回复完成: openKfId={}, success={}", msg.getOpenKfId(), result != null);
    }

    private String routeToAssistant(MsgItem msg, String userMessage) {
        var binding = bindingRepo.findByOpenKfIdAndEnabledTrue(msg.getOpenKfId()).orElse(null);
        if (binding == null) {
            log.warn("企微客服账号没有启用的 Assistant v2 绑定: openKfId={}", msg.getOpenKfId());
            return properties.getFallbackReply();
        }

        try {
            return assistants.execute(request(binding, msg, userMessage));
        } catch (RuntimeException failure) {
            log.error(
                    "企微客服 Assistant v2 执行失败: bindingId={}, errorType={}",
                    binding.getId(),
                    failure.getClass().getSimpleName());
            return properties.getFallbackReply();
        }
    }

    private Request request(WecomKfAccountBinding binding, MsgItem msg, String userMessage) {
        return new Request(
                binding.getOrgId(),
                binding.getOwnerId(),
                binding.getAssistantId(),
                binding.getAssistantVersion() == null ? 0 : binding.getAssistantVersion(),
                "wecom_kf",
                binding.getId().toString(),
                msg.getExternalUserId(),
                msg.getMsgid(),
                userMessage,
                Instant.now());
    }
}
