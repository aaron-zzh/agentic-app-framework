package com.xuejiai.aaf.module.billing.service;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.cache.ConfigCacheManager;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.ai.chat.TokenUsageEvent;
import com.xuejiai.aaf.framework.intelligent.core.AiUsage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Token 用量事件监听器——事后按真实 token 数扣积分。
 *
 * <p>异步执行，不阻塞 AI 响应返回。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenUsageEventListener {

    private final AiCreditGuard creditGuard;
    private final ConfigCacheManager configCacheManager;

    @Async
    @EventListener
    public void onTokenUsage(TokenUsageEvent event) {
        if (event.userId() == null) {
            log.debug("TokenUsageEvent userId 为空，跳过结算: {}", event);
            return;
        }
        var model = event.modelId() != null ? configCacheManager.getAiModel(event.modelId()) : null;
        final long input = event.promptTokens();
        final long output = event.completionTokens();
        // capability 由发布方传入（chat / vision 等规范小写值），缺省回退到 "chat"
        final String capability = event.capability() != null ? event.capability() : "chat";
        log.info(
                "Chat 计费结算: userId={}, modelId={}, capability={}, promptTokens={},"
                        + " completionTokens={}, usageId={}",
                event.userId(),
                event.modelId(),
                capability,
                input,
                output,
                event.usageId());
        AiUsage usage =
                new AiUsage() {
                    @Override
                    public Map<String, Object> standardUsage() {
                        return Map.of("inputTokens", input, "outputTokens", output);
                    }
                };
        creditGuard.settleByUsage(event.userId(), model, usage, capability, null);
    }
}
