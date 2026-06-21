package com.xuejiai.aaf.module.channel.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.xuejiai.aaf.common.enums.channel.ChannelTypeEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.channel.domain.WebhookConfig;
import com.xuejiai.aaf.module.channel.domain.WebhookLog;
import com.xuejiai.aaf.module.channel.repository.WebhookConfigRepository;
import com.xuejiai.aaf.module.channel.repository.WebhookLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Webhook 服务。
 *
 * <p>出站：事件触发时推送到已注册的 Webhook（HMAC 签名 + 重试）。
 *
 * <p>入站：接收外部 Webhook 推送，转换为 UnifiedMessage 走 ChannelMessageRouter。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookConfigRepository configRepository;
    private final WebhookLogRepository logRepository;
    private final RestClient.Builder restClientBuilder;
    private final ChannelMessageRouter router;

    // ==================== 配置管理 ====================

    @Transactional
    public WebhookConfig create(WebhookConfig config) {
        return configRepository.save(config);
    }

    @Transactional
    public WebhookConfig update(Long id, WebhookConfig updated) {
        var config =
                configRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "Webhook 配置不存在"));
        config.setName(updated.getName());
        config.setUrl(updated.getUrl());
        config.setEventTypes(updated.getEventTypes());
        config.setSecret(updated.getSecret());
        config.setStatus(updated.getStatus());
        config.setMaxRetries(updated.getMaxRetries());
        return configRepository.save(config);
    }

    @Transactional
    public void delete(Long id) {
        configRepository.deleteById(id);
    }

    public List<WebhookConfig> listActive() {
        return configRepository.findByStatusAndDeletedFalse("active");
    }

    // ==================== 出站推送 ====================

    /**
     * 触发事件推送到所有订阅该事件的 Webhook。
     *
     * @param eventType 事件类型
     * @param payload 事件数据
     */
    @Transactional
    public void triggerEvent(String eventType, Map<String, Object> payload) {
        var configs =
                configRepository.findByDirectionAndStatusAndDeletedFalse("outbound", "active");
        for (var config : configs) {
            if (subscribesEvent(config, eventType)) {
                pushToWebhook(config, eventType, payload);
            }
        }
    }

    /** 重试失败的 Webhook 推送（供定时任务调用）。 */
    @Transactional
    public void retryFailed() {
        var pendingLogs =
                logRepository.findByStatusAndNextRetryTimeBeforeAndDeletedFalse(
                        "failed", LocalDateTime.now());
        for (var webhookLog : pendingLogs) {
            var config = configRepository.findById(webhookLog.getWebhookId()).orElse(null);
            if (config == null || !"active".equals(config.getStatus())) {
                webhookLog.setStatus("abandoned");
                logRepository.save(webhookLog);
                continue;
            }
            if (webhookLog.getRetryCount() >= config.getMaxRetries()) {
                webhookLog.setStatus("abandoned");
                // 连续失败过多，停用 Webhook
                config.setFailureCount(config.getFailureCount() + 1);
                if (config.getFailureCount() >= 10) {
                    config.setStatus("failed");
                    log.warn("Webhook 连续失败过多，已停用: id={}, url={}", config.getId(), config.getUrl());
                }
                configRepository.save(config);
                logRepository.save(webhookLog);
                continue;
            }
            executePush(config, webhookLog);
        }
    }

    // ==================== 入站接收 ====================

    /**
     * 接收外部 Webhook 推送，转换为 UnifiedMessage 走路由。
     *
     * @param rawPayload 原始请求体
     * @return 路由处理结果
     */
    public String receiveInbound(String rawPayload) {
        return router.routeInbound(ChannelTypeEnum.WEBHOOK, rawPayload);
    }

    /**
     * 验证入站 Webhook 签名。
     *
     * @param webhookId Webhook 配置 ID
     * @param signature 请求头中的签名
     * @param body 请求体
     * @return 验证是否通过
     */
    public boolean verifyInboundSignature(Long webhookId, String signature, String body) {
        if (signature == null || signature.isBlank()) {
            return true;
        }
        var config = configRepository.findById(webhookId).orElse(null);
        if (config == null || config.getSecret() == null || config.getSecret().isBlank()) {
            return true;
        }
        var computed = computeHmac(body, config.getSecret());
        return computed.equals(signature);
    }

    // ==================== 内部方法 ====================

    private void pushToWebhook(
            WebhookConfig config, String eventType, Map<String, Object> payload) {
        var webhookLog = new WebhookLog();
        webhookLog.setWebhookId(config.getId());
        webhookLog.setEventType(eventType);
        webhookLog.setPushTime(LocalDateTime.now());
        try {
            var body =
                    JsonUtils.toJsonString(
                            Map.of(
                                    "event_type", eventType,
                                    "timestamp", System.currentTimeMillis(),
                                    "data", payload));
            webhookLog.setRequestBody(body);
            executePush(config, webhookLog);
        } catch (Exception e) {
            webhookLog.setStatus("failed");
            webhookLog.setFailureReason(e.getMessage());
            webhookLog.setNextRetryTime(computeNextRetry(0));
            logRepository.save(webhookLog);
        }
    }

    private void executePush(WebhookConfig config, WebhookLog webhookLog) {
        try {
            var requestBody = webhookLog.getRequestBody();
            var signature = computeHmac(requestBody, config.getSecret());

            var response =
                    restClientBuilder
                            .build()
                            .post()
                            .uri(config.getUrl())
                            .header("X-Webhook-Signature", signature)
                            .header("X-Webhook-Event", webhookLog.getEventType())
                            .header("Content-Type", "application/json")
                            .body(requestBody)
                            .retrieve()
                            .toEntity(String.class);

            webhookLog.setResponseStatus(response.getStatusCode().value());
            webhookLog.setResponseBody(truncate(response.getBody(), 2000));

            if (response.getStatusCode().is2xxSuccessful()) {
                webhookLog.setStatus("success");
                config.setFailureCount(0);
            } else {
                markFailed(webhookLog, "HTTP " + response.getStatusCode().value());
            }
        } catch (Exception e) {
            markFailed(webhookLog, e.getMessage());
        }
        configRepository.save(config);
        logRepository.save(webhookLog);
    }

    private void markFailed(WebhookLog webhookLog, String reason) {
        webhookLog.setStatus("failed");
        webhookLog.setFailureReason(truncate(reason, 500));
        webhookLog.setRetryCount(webhookLog.getRetryCount() + 1);
        webhookLog.setNextRetryTime(computeNextRetry(webhookLog.getRetryCount()));
    }

    /** 指数退避：2^retryCount 分钟 */
    private LocalDateTime computeNextRetry(int retryCount) {
        var delayMinutes = (long) Math.pow(2, retryCount);
        return LocalDateTime.now().plusMinutes(delayMinutes);
    }

    private boolean subscribesEvent(WebhookConfig config, String eventType) {
        if (config.getEventTypes() == null || config.getEventTypes().isBlank()) {
            return true; // 未指定则订阅所有
        }
        return config.getEventTypes().contains(eventType);
    }

    private String computeHmac(String data, String secret) {
        if (secret == null || secret.isBlank()) {
            return "";
        }
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (var b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("HMAC 计算失败: {}", e.getMessage());
            return "";
        }
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }
}
