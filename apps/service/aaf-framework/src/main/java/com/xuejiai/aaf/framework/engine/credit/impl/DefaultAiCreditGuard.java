package com.xuejiai.aaf.framework.engine.credit.impl;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.constant.SysConfigKeys;
import com.xuejiai.aaf.common.enums.pay.CreditTransactionSourceEnum;
import com.xuejiai.aaf.common.exception.InsufficientCreditsException;
import com.xuejiai.aaf.framework.engine.cache.ConfigCacheManager;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.framework.intelligent.ai.chat.CreditLowEvent;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 积分门控默认实现。
 *
 * <p>积分轨 fail-closed：userId=null 或余额 ≤ 0 时拒绝。 余额低于预警阈值时异步发 {@link CreditLowEvent}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAiCreditGuard implements AiCreditGuard {

    /** 1元 = 100积分（积分单位为"分"） */
    private static final double YUAN_TO_CREDIT = 100.0;

    /** 模型价格单位：元/千token */
    private static final double PER_K_TOKENS = 1000.0;

    private final CreditService creditService;
    private final SystemConfigService configService;
    private final ApplicationEventPublisher eventPublisher;
    private final ConfigCacheManager configCacheManager;

    @Override
    public int getMarkupRate() {
        return configService.getInteger(SysConfigKeys.Ai.TOKEN_MARKUP_RATE, 10);
    }

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
    public void precheck(Long userId, String capability, long estimatedCost) {
        if (userId == null) {
            throw new IllegalStateException("AI 门控：userId 为空，无法归账，拒绝调用 capability=" + capability);
        }
        long balance = creditService.getBalance(userId);
        long overdraft = configService.getInteger(SysConfigKeys.Ai.CREDIT_OVERDRAFT_LIMIT, 0);
        long minRequired = estimatedCost > 0 ? estimatedCost : 1;
        if (balance + overdraft < minRequired) {
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
        settleByModel(userId, modelId, inputTokens, outputTokens, bizId, null);
    }

    @Override
    public void settleByModel(
            Long userId,
            Long modelId,
            long inputTokens,
            long outputTokens,
            String bizId,
            String remark) {
        if (userId == null) return;
        int markup = configService.getInteger(SysConfigKeys.Ai.TOKEN_MARKUP_RATE, 10);
        long creditCost;
        if (modelId != null) {
            var prices = getModelPrices(modelId);
            if (prices != null) {
                double cost =
                        (inputTokens * prices[0] + outputTokens * prices[1])
                                / PER_K_TOKENS
                                * YUAN_TO_CREDIT
                                * markup;
                creditCost = Math.max(1, Math.round(cost));
                log.info(
                        "AI token 计费: cap={}, userId={}, modelId={}, inTokens={}, outTokens={}, "
                                + "inPricePerK={}, outPricePerK={}, markup={}, rawCost={}, creditCost={}",
                        bizId,
                        userId,
                        modelId,
                        inputTokens,
                        outputTokens,
                        prices[0],
                        prices[1],
                        markup,
                        cost,
                        creditCost);
            } else {
                creditCost = fallbackCreditCost(inputTokens + outputTokens);
                log.info(
                        "AI token 计费(降级-模型无单价): cap={}, userId={}, modelId={}, totalTokens={}, markup={}, creditCost={}",
                        bizId,
                        userId,
                        modelId,
                        inputTokens + outputTokens,
                        markup,
                        creditCost);
            }
        } else {
            creditCost = fallbackCreditCost(inputTokens + outputTokens);
            log.info(
                    "AI token 计费(降级-无modelId): cap={}, userId={}, totalTokens={}, markup={}, creditCost={}",
                    bizId,
                    userId,
                    inputTokens + outputTokens,
                    markup,
                    creditCost);
        }
        try {
            long overdraft = configService.getInteger(SysConfigKeys.Ai.CREDIT_OVERDRAFT_LIMIT, 0);
            creditService.spendAllowOverdraft(
                    userId,
                    creditCost,
                    CreditTransactionSourceEnum.AI_CONSUME.getCode(),
                    bizId, // category = bizId (capability)
                    bizId,
                    overdraft,
                    remark);
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
        settlePerUse(userId, modelId, bizId, null);
    }

    @Override
    public void settlePerUse(Long userId, Long modelId, String bizId, String remark) {
        if (userId == null) return;
        int markup = configService.getInteger(SysConfigKeys.Ai.TOKEN_MARKUP_RATE, 10);
        long creditCost = 1;
        if (modelId != null) {
            var model = getAiModel(modelId);
            if (model != null && model.getModelPrice() != null) {
                creditCost =
                        Math.max(
                                1,
                                Math.round(
                                        model.getModelPrice().doubleValue()
                                                * YUAN_TO_CREDIT
                                                * markup));
            }
        }
        try {
            creditService.spendAllowOverdraft(
                    userId,
                    creditCost,
                    CreditTransactionSourceEnum.AI_CONSUME.getCode(),
                    bizId, // category = bizId (capability)
                    bizId,
                    0L,
                    remark);
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
        // 兜底单价 0.072 元/千token（参考 GPT-3.5 量级）
        return Math.max(1, Math.round(tokens * 0.072 / PER_K_TOKENS * YUAN_TO_CREDIT * markup));
    }

    private double[] getModelPrices(Long modelId) {
        var model = configCacheManager.getAiModel(modelId);
        if (model == null) return null;
        return new double[] {
            model.getInputPricePerK() != null ? model.getInputPricePerK().doubleValue() : 0.036,
            model.getOutputPricePerK() != null ? model.getOutputPricePerK().doubleValue() : 0.108
        };
    }

    private AiModel getAiModel(Long modelId) {
        return configCacheManager.getAiModel(modelId);
    }
}
