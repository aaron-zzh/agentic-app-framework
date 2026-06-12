package com.xuejiai.aaf.module.billing.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.constant.SysConfigKeys;
import com.xuejiai.aaf.common.exception.InsufficientCreditsException;
import com.xuejiai.aaf.framework.engine.cache.ConfigCacheManager;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.framework.intelligent.ai.chat.CreditLowEvent;
import com.xuejiai.aaf.module.system.config.service.SystemConfigService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 模型价格缓存组件——通过 ConfigCacheManager（本地+Redis 两级缓存）获取模型价格。 */
@Component
@RequiredArgsConstructor
class ModelPriceCache {

    private final ConfigCacheManager configCacheManager;

    /** 查模型 input/output 单价（元/千token） */
    public double[] getPrices(Long modelId) {
        var model = configCacheManager.getAiModel(modelId);
        if (model == null) return null;
        return new double[] {
            model.getInputPricePerK() != null ? model.getInputPricePerK().doubleValue() : 0.036,
            model.getOutputPricePerK() != null ? model.getOutputPricePerK().doubleValue() : 0.108
        };
    }

    /** 查完整模型对象（用于按次计费取 modelPrice） */
    public com.xuejiai.aaf.framework.intelligent.core.model.AiModel getModel(Long modelId) {
        return configCacheManager.getAiModel(modelId);
    }
}

/**
 * AI 积分门控默认实现。
 *
 * <p>积分轨 fail-closed：userId=null 或余额 ≤ 0 时拒绝。 余额低于预警阈值（sys_config: ai.credit_warn_threshold，默认
 * 10）时异步发 {@link CreditLowEvent}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAiCreditGuard implements AiCreditGuard {

    private final CreditService creditService;
    private final SystemConfigService configService;
    private final ApplicationEventPublisher eventPublisher;
    private final ModelPriceCache modelPriceCache;

    @Override
    public void precheck(Long userId, String capability) {
        if (userId == null) {
            throw new IllegalStateException("AI 门控：userId 为空，无法归账，拒绝调用 capability=" + capability);
        }
        long balance = creditService.getBalance(userId);
        if (balance <= 0) {
            throw new InsufficientCreditsException(userId, balance);
        }
        long threshold = configService.getInteger(SysConfigKeys.Ai.CREDIT_WARN_THRESHOLD, 10);
        if (balance <= threshold) {
            eventPublisher.publishEvent(new CreditLowEvent(userId, balance, threshold));
        }
    }

    @Override
    public void settle(Long userId, String capability, long actualCost) {
        settle(userId, capability, actualCost, null);
    }

    @Override
    public void settle(Long userId, String capability, long actualCost, String bizId) {
        var creditCost = fallbackCreditCost(actualCost);
        if (userId == null || creditCost <= 0) return;
        try {
            creditService.spend(userId, creditCost, capability, bizId);
        } catch (Exception e) {
            log.warn(
                    "AI 积分扣减失败: userId={}, capability={}, cost={}, err={}",
                    userId,
                    capability,
                    creditCost,
                    e.getMessage());
        }
    }

    @Override
    public void settleByModel(
            Long userId, Long modelId, long inputTokens, long outputTokens, String bizId) {
        if (userId == null) return;
        int markup = configService.getInteger(SysConfigKeys.Ai.TOKEN_MARKUP_RATE, 10);
        long creditCost;
        if (modelId != null) {
            var prices = modelPriceCache.getPrices(modelId);
            if (prices != null) {
                double cost =
                        (inputTokens * prices[0] + outputTokens * prices[1]) / 1000.0 * markup;
                creditCost = Math.max(1, Math.round(cost));
            } else {
                creditCost = fallbackCreditCost(inputTokens + outputTokens);
            }
        } else {
            creditCost = fallbackCreditCost(inputTokens + outputTokens);
        }
        try {
            creditService.spend(userId, creditCost, "chat", bizId);
        } catch (Exception e) {
            log.warn(
                    "AI 积分扣减失败: userId={}, modelId={}, cost={}, err={}",
                    userId,
                    modelId,
                    creditCost,
                    e.getMessage());
        }
    }

    @Override
    public void settlePerUse(Long userId, Long modelId, String bizId) {
        if (userId == null) return;
        int markup = configService.getInteger(SysConfigKeys.Ai.TOKEN_MARKUP_RATE, 10);
        long creditCost = 1; // 默认1积分/次
        if (modelId != null) {
            var model = modelPriceCache.getModel(modelId);
            if (model != null && model.getModelPrice() != null) {
                creditCost = Math.max(1, Math.round(model.getModelPrice().doubleValue() * markup));
            }
        }
        try {
            creditService.spend(userId, creditCost, "image", bizId);
        } catch (Exception e) {
            log.warn(
                    "按次积分扣减失败: userId={}, modelId={}, cost={}, err={}",
                    userId,
                    modelId,
                    creditCost,
                    e.getMessage());
        }
    }

    private long fallbackCreditCost(long tokens) {
        int markup = configService.getInteger(SysConfigKeys.Ai.TOKEN_MARKUP_RATE, 10);
        return Math.max(1, Math.round(tokens * 0.072 * markup / 1000.0));
    }
}
