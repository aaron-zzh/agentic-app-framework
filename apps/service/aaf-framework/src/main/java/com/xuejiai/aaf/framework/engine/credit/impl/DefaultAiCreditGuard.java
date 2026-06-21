package com.xuejiai.aaf.framework.engine.credit.impl;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.constant.SysConfigKeys;
import com.xuejiai.aaf.common.enums.ai.AiQuotaTypeEnum;
import com.xuejiai.aaf.common.enums.pay.CreditTransactionSourceEnum;
import com.xuejiai.aaf.common.exception.InsufficientCreditsException;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.cache.ConfigCacheManager;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.engine.credit.AiUsageRecord;
import com.xuejiai.aaf.framework.engine.credit.AiUsageRecordRepository;
import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.framework.intelligent.ai.chat.CreditLowEvent;
import com.xuejiai.aaf.framework.intelligent.core.AiUsage;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 积分门控默认实现。
 *
 * <p>积分轨 fail-closed：userId=null 或余额 ≤ 0 时拒绝。余额低于预警阈值时异步发 {@link CreditLowEvent}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAiCreditGuard implements AiCreditGuard {

    /** 模型价格单位：元/千 token */
    private static final double PER_K_TOKENS = 1000.0;

    private final CreditService creditService;
    private final SystemConfigService configService;
    private final ApplicationEventPublisher eventPublisher;
    private final ConfigCacheManager configCacheManager;
    private final AiUsageRecordRepository usageRecordRepository;

    @Override
    public int getMarkupRate() {
        return configService.getInteger(SysConfigKeys.Ai.TOKEN_MARKUP_RATE, 5);
    }

    @Override
    public boolean hasBudget(Long userId, long estimatedCost) {
        if (userId == null) return false;
        long balance = creditService.getBalance(userId);
        long overdraft = configService.getInteger(SysConfigKeys.Ai.CREDIT_OVERDRAFT_LIMIT, 0);
        long minRequired = estimatedCost > 0 ? estimatedCost : 1;
        return balance + overdraft >= minRequired;
    }

    @Override
    public void precheck(Long userId, String capability, long estimatedCost) {
        if (userId == null) {
            throw new IllegalStateException("AI 门控：userId 为空，无法归账，拒绝调用 capability=" + capability);
        }
        long balance = creditService.getBalance(userId);
        long overdraft = configService.getInteger(SysConfigKeys.Ai.CREDIT_OVERDRAFT_LIMIT, 0);
        long minRequired = estimatedCost > 0 ? estimatedCost : 1;
        boolean ok = balance + overdraft >= minRequired;
        log.debug(
                "AI 积分预检: userId={}, capability={}, balance={}, overdraft={}, estimatedCost={},"
                        + " minRequired={}, markup={}, ok={}",
                userId,
                capability,
                balance,
                overdraft,
                estimatedCost,
                minRequired,
                getMarkupRate(),
                ok);
        if (!ok) {
            throw new InsufficientCreditsException(userId, balance, minRequired, overdraft);
        }
        checkLowBalance(userId, balance);
    }

    @Override
    public void settleByUsage(
            Long userId, AiModel model, AiUsage usage, String capability, String remark) {
        settleByUsageReturningTxId(userId, model, usage, capability, remark);
    }

    @Override
    public Long settleByUsageReturningTxId(
            Long userId, AiModel model, AiUsage usage, String capability, String remark) {
        if (userId == null) return null;
        Long modelId = model != null ? model.getId() : null;
        int quotaType = model != null && model.getQuotaType() != null ? model.getQuotaType() : 0;

        long[] result = calcCost(model, usage, quotaType);
        long creditCost = result[0];
        double costYuan = Double.longBitsToDouble(result[1]);

        log.info(
                "AI 结算明细: userId={}, capability={}, modelId={}, quotaType={}, in={}, out={},"
                        + " yuan={}, markup={}, credit={}",
                userId,
                capability,
                model != null ? model.getModelId() : null,
                quotaType,
                usage.inputTokens(),
                usage.outputTokens(),
                String.format("%.6f", costYuan),
                getMarkupRate(),
                creditCost);

        Long creditTxId = doSpend(userId, creditCost, capability, remark);
        if (creditTxId == null && creditCost > 0) return null; // 扣减失败，不写用量记录

        saveUsageRecord(
                userId, modelId, capability, quotaType, creditCost, costYuan, creditTxId, usage);
        log.info(
                "AI 积分扣减成功: userId={}, capability={}, modelId={}, credit={}, yuan={}, txId={}",
                userId,
                capability,
                model != null ? model.getModelId() : null,
                creditCost,
                String.format("%.6f", costYuan),
                creditTxId);
        return creditTxId;
    }

    @Override
    public Long refund(Long creditTxId, String reason) {
        if (creditTxId == null) return null;
        try {
            return creditService.refund(creditTxId, reason);
        } catch (Exception e) {
            log.warn("[refund] 积分退还失败: creditTxId={}, err={}", creditTxId, e.getMessage());
            return null;
        }
    }

    /**
     * 按 quotaType 计算积分成本和元成本，返回 [creditCost, costYuanBits]。
     *
     * <p><b>关于 {@code Math.max(1, ...)} 兜底：</b>每次结算至少扣 1 积分，作用：
     *
     * <ul>
     *   <li>避免 {@code credit_transaction} 表出现 amount=0 的脏流水
     *   <li>覆盖系统调用开销（哪怕真实成本只有 0.0001 元）
     * </ul>
     *
     * <p><b>批量场景注意</b>：若一次调用产生多次 settle（如知识库批量 embedding 1000 段）， 每段 token 极少时单段成本 &lt; 1
     * 积分会触发兜底，累计虚高。 <b>调用方必须把 usage 聚合后再调用一次 settleByUsage</b>， 而不是每个元素调一次。chat 流式已正确实现（{@code
     * ResilientChatService.withStreamUsage} 在 onComplete 一次性聚合发事件）。
     */
    private long[] calcCost(AiModel model, AiUsage usage, int quotaType) {
        int markup = getMarkupRate();
        Long modelId = model != null ? model.getId() : null;
        AiQuotaTypeEnum type;
        try {
            type = AiQuotaTypeEnum.of(quotaType);
        } catch (Exception e) {
            type = AiQuotaTypeEnum.TOKEN;
        }

        return switch (type) {
            case PER_USE -> {
                long cost =
                        AiCreditGuard.calcPerUseCost(
                                model != null ? model.getModelPrice() : null, markup);
                double yuan =
                        model != null && model.getModelPrice() != null
                                ? model.getModelPrice().doubleValue()
                                : 0;
                yield new long[] {cost, Double.doubleToLongBits(yuan)};
            }
            case PER_UNIT -> {
                // 按单元计费（如图像按张、视频按分辨率），从 usage.count() 读实际单元数
                int unitCount = usage.count();
                double unitPrice =
                        model != null && model.getModelPrice() != null
                                ? model.getModelPrice().doubleValue()
                                : 0.04;
                double yuan = unitPrice * unitCount;
                yield new long[] {
                    Math.max(1, Math.round(yuan * YUAN_TO_CREDIT * markup)),
                    Double.doubleToLongBits(yuan)
                };
            }
            case PER_SEC -> {
                int duration = Math.max(1, usage.duration());
                double pricePerSec =
                        model != null && model.getModelPrice() != null
                                ? model.getModelPrice().doubleValue()
                                : 0;
                double yuan = pricePerSec * duration;
                yield new long[] {
                    Math.max(1, Math.round(yuan * YUAN_TO_CREDIT * markup)),
                    Double.doubleToLongBits(yuan)
                };
            }
            default -> {
                long input = usage.inputTokens(), output = usage.outputTokens();
                var prices = modelId != null ? getModelPrices(modelId) : null;
                if (prices != null) {
                    double yuan = (input * prices[0] + output * prices[1]) / PER_K_TOKENS;
                    yield new long[] {
                        Math.max(1, Math.round(yuan * YUAN_TO_CREDIT * markup)),
                        Double.doubleToLongBits(yuan)
                    };
                }
                yield new long[] {fallbackCreditCost(input + output), Double.doubleToLongBits(0)};
            }
        };
    }

    /** 扣积分，返回流水 ID；失败返回 null 并记录 warn。 */
    private Long doSpend(Long userId, long creditCost, String capability, String remark) {
        try {
            long overdraft = configService.getInteger(SysConfigKeys.Ai.CREDIT_OVERDRAFT_LIMIT, 0);
            return creditService.spend(
                    userId,
                    creditCost,
                    CreditTransactionSourceEnum.AI_CONSUME.getCode(),
                    capability, // category：AI 能力维度（ocr/chat/image_gen 等）
                    null, // bizId：ai_usage_record.credit_tx_id 反向关联，此处无需重复存
                    overdraft,
                    remark,
                    com.xuejiai.aaf.common.enums.pay.CreditBizTypeEnum.AI_USAGE.getCode());
        } catch (Exception e) {
            log.warn(
                    "AI 积分扣减失败: userId={}, capability={}, cost={}, err={}",
                    userId,
                    capability,
                    creditCost,
                    e.getMessage());
            return null;
        }
    }

    /** 写 AiUsageRecord，失败不影响结算。 */
    private void saveUsageRecord(
            Long userId,
            Long modelId,
            String capability,
            int quotaType,
            long creditCost,
            double costYuan,
            Long creditTxId,
            AiUsage usage) {
        try {
            var record = new AiUsageRecord();
            record.setUserId(userId);
            record.setModelId(modelId);
            record.setCapability(capability);
            record.setQuotaType((short) quotaType);
            record.setCostYuan(java.math.BigDecimal.valueOf(costYuan));
            record.setCreditAmount(creditCost);
            record.setCreditTxId(creditTxId);
            record.setUsage(JsonUtils.toJsonString(usage.standardUsage()));
            record.setRawUsage(JsonUtils.toJsonString(usage.rawUsage()));
            usageRecordRepository.save(record);
        } catch (Exception e) {
            log.warn(
                    "写入 AiUsageRecord 失败（不影响结算）: userId={}, capability={}, err={}",
                    userId,
                    capability,
                    e.getMessage());
        }
    }

    // ========== 私有工具方法 ==========

    private void checkLowBalance(Long userId, long balance) {
        long threshold = configService.getInteger(SysConfigKeys.Ai.CREDIT_WARN_THRESHOLD, 10);
        if (balance <= threshold) {
            eventPublisher.publishEvent(new CreditLowEvent(userId, balance, threshold));
        }
    }

    private long fallbackCreditCost(long tokens) {
        int markup = getMarkupRate();
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
}
