/**
 * Token 计量服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.core.token;

import java.time.LocalDateTime;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.ai.chat.TokenUsageEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Token 计量系统：记录用量、检查配额。 监听 AgentScope 的 getChatUsage() 和 AAF 的 TokenUsageEvent。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenMeteringService {

    private final TokenUsageRepository repository;
    private final TokenQuotaService quotaService;

    /** 监听 Token 用量事件，异步持久化 */
    @Async
    @EventListener
    public void onTokenUsage(TokenUsageEvent event) {
        var record = new TokenUsageRecord();
        record.setUserId(event.userId());
        record.setModelId(event.model());
        record.setPromptTokens(event.promptTokens());
        record.setCompletionTokens(event.completionTokens());
        repository.save(record);
    }

    /** 检查用户是否超出配额 */
    public boolean isQuotaExceeded(Long userId) {
        var quota = quotaService.getQuota(userId);
        if (quota <= 0) {
            return false; // 无限制
        }
        var used =
                repository.sumTotalTokensByUserSince(userId, LocalDateTime.now().withDayOfMonth(1));
        return used >= quota;
    }

    /** 获取用户当月已用量 */
    public long getMonthlyUsage(Long userId) {
        return repository.sumTotalTokensByUserSince(userId, LocalDateTime.now().withDayOfMonth(1));
    }

    /** 记录用量（供 AgentScope Hook 调用） */
    public void record(
            Long userId,
            String modelId,
            String conversationId,
            long promptTokens,
            long completionTokens) {
        var record = new TokenUsageRecord();
        record.setUserId(userId);
        record.setModelId(modelId);
        record.setConversationId(conversationId);
        record.setPromptTokens(promptTokens);
        record.setCompletionTokens(completionTokens);
        repository.save(record);
    }
}
