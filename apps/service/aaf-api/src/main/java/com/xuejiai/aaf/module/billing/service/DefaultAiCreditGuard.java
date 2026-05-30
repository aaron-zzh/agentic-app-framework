package com.xuejiai.aaf.module.billing.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.constant.SysConfigKeys;
import com.xuejiai.aaf.common.exception.InsufficientCreditsException;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.framework.intelligent.ai.chat.CreditLowEvent;
import com.xuejiai.aaf.module.system.config.service.SystemConfigService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 积分门控默认实现。
 *
 * <p>积分轨 fail-closed：userId=null 或余额 ≤ 0 时拒绝。
 * 余额低于预警阈值（sys_config: ai.credit_warn_threshold，默认 10）时异步发 {@link CreditLowEvent}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAiCreditGuard implements AiCreditGuard {

    private final CreditService creditService;
    private final SystemConfigService configService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void precheck(Long userId, String capability) {
        if (userId == null) {
            throw new IllegalStateException("AI 门控：userId 为空，无法归账，拒绝调用 capability=" + capability);
        }
        long balance = creditService.getBalance(userId);
        if (balance <= 0) {
            throw new InsufficientCreditsException(userId, balance);
        }
        // 低于预警阈值时异步通知，不阻塞调用
        long threshold = configService.getInteger(SysConfigKeys.Ai.CREDIT_WARN_THRESHOLD, 10);
        if (balance <= threshold) {
            eventPublisher.publishEvent(new CreditLowEvent(userId, balance, threshold));
        }
    }

    @Override
    public void settle(Long userId, String capability, long actualCost) {
        if (userId == null || actualCost <= 0) return;
        try {
            creditService.spend(userId, actualCost, capability, null);
        } catch (Exception e) {
            log.warn("AI 积分扣减失败，跳过（不回滚 AI 调用）: userId={}, capability={}, cost={}, err={}",
                    userId, capability, actualCost, e.getMessage());
        }
    }
}
