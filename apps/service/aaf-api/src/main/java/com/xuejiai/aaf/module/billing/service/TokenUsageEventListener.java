package com.xuejiai.aaf.module.billing.service;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.ai.chat.TokenUsageEvent;

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

    @Async
    @EventListener
    public void onTokenUsage(TokenUsageEvent event) {
        if (event.userId() == null) return;
        creditGuard.settle(event.userId(), "chat", event.totalTokens());
    }
}
