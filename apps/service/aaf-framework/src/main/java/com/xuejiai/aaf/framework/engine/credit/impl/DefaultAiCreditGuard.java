package com.xuejiai.aaf.framework.engine.credit.impl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.constant.SysConfigKeys;
import com.xuejiai.aaf.common.enums.ai.AiQuotaTypeEnum;
import com.xuejiai.aaf.common.enums.pay.CreditTransactionCategoryEnum;
import com.xuejiai.aaf.common.enums.pay.CreditTransactionSourceEnum;
import com.xuejiai.aaf.common.exception.InsufficientCreditsException;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard.IdempotentSettlementResult;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard.IdempotentUsageSettlement;
import com.xuejiai.aaf.framework.engine.credit.AiUsageRecord;
import com.xuejiai.aaf.framework.engine.credit.AiUsageRecordRepository;
import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.framework.intelligent.ai.chat.CreditLowEvent;
import com.xuejiai.aaf.framework.intelligent.ai.model3d.Model3dGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.VideoTaskResult;
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
        Objects.requireNonNull(userId, "AI 结算 userId 不能为空");
        Objects.requireNonNull(model, "AI 结算 model 不能为空");
        Objects.requireNonNull(model.getId(), "AI 结算 model.id 不能为空");
        Objects.requireNonNull(usage, "AI 结算 usage 不能为空");
        Long modelId = model.getId();
        int quotaType = model.getQuotaType() == null ? 0 : model.getQuotaType();

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

        Long creditTxId =
                doSpend(
                        userId,
                        creditCost,
                        CreditTransactionCategoryEnum.fromCapability(capability),
                        remark);
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
    @Transactional
    public IdempotentSettlementResult settleIdempotently(IdempotentUsageSettlement settlement) {
        var model = settlement.model();
        var quotaType = model.getQuotaType() == null ? (short) 0 : model.getQuotaType();
        var costYuan = settlement.costYuan().setScale(6, java.math.RoundingMode.HALF_UP);
        var creditCost =
                Math.max(
                        1L,
                        costYuan.multiply(BigDecimal.valueOf(YUAN_TO_CREDIT))
                                .multiply(BigDecimal.valueOf(getMarkupRate()))
                                .setScale(0, java.math.RoundingMode.HALF_UP)
                                .longValueExact());
        var usageJson = JsonUtils.toJsonString(settlement.usage().standardUsage());
        var rawUsageJson = JsonUtils.toJsonString(settlement.usage().rawUsage());
        var digest =
                settlementDigest(
                        settlement, quotaType, costYuan, creditCost, usageJson, rawUsageJson);

        var claimed =
                usageRecordRepository.claim(
                        settlement.usageKey(),
                        digest,
                        settlement.tenantId(),
                        settlement.taskId(),
                        settlement.executionId(),
                        settlement.fencingToken(),
                        settlement.occurredAt(),
                        settlement.userId(),
                        model.getId(),
                        settlement.capability(),
                        quotaType,
                        costYuan,
                        creditCost,
                        usageJson,
                        rawUsageJson);
        if (claimed == 0) {
            return existingSettlement(settlement, digest);
        }

        var overdraft = configService.getInteger(SysConfigKeys.Ai.CREDIT_OVERDRAFT_LIMIT, 0);
        var creditTxId =
                creditService.spend(
                        settlement.userId(),
                        creditCost,
                        CreditTransactionSourceEnum.AI_CONSUME.getCode(),
                        CreditTransactionCategoryEnum.fromCapability(settlement.capability()),
                        settlement.usageKey(),
                        overdraft,
                        settlement.remark(),
                        com.xuejiai.aaf.common.enums.pay.CreditBizTypeEnum.AI_USAGE.getCode());
        if (creditTxId == null
                || usageRecordRepository.completeSettlement(settlement.usageKey(), creditTxId)
                        != 1) {
            throw new IllegalStateException("AI 幂等结算未能关联真实积分流水: " + settlement.usageKey());
        }
        return new IdempotentSettlementResult(settlement.usageKey(), creditTxId, true);
    }

    private IdempotentSettlementResult existingSettlement(
            IdempotentUsageSettlement settlement, String digest) {
        var existing =
                usageRecordRepository
                        .findByUsageKey(settlement.usageKey())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "usageKey claim 状态丢失: " + settlement.usageKey()));
        if (!Objects.equals(existing.getSettlementDigest(), digest)
                || !Objects.equals(existing.getTenantId(), settlement.tenantId())
                || !Objects.equals(existing.getTaskId(), settlement.taskId())
                || !Objects.equals(existing.getExecutionId(), settlement.executionId())
                || !Objects.equals(existing.getFencingToken(), settlement.fencingToken())
                || !Objects.equals(existing.getUserId(), settlement.userId())
                || !Objects.equals(existing.getModelId(), settlement.model().getId())
                || !Objects.equals(existing.getCapability(), settlement.capability())) {
            throw new IllegalStateException("usageKey 已绑定不同用量内容: " + settlement.usageKey());
        }
        if (existing.getCreditTxId() == null) {
            throw new IllegalStateException("usageKey 已存在但缺少真实积分流水: " + settlement.usageKey());
        }
        return new IdempotentSettlementResult(
                settlement.usageKey(), existing.getCreditTxId(), false);
    }

    private String settlementDigest(
            IdempotentUsageSettlement settlement,
            short quotaType,
            BigDecimal costYuan,
            long creditCost,
            String usageJson,
            String rawUsageJson) {
        var values = new LinkedHashMap<String, Object>();
        values.put("usageKey", settlement.usageKey());
        values.put("tenantId", settlement.tenantId());
        values.put("taskId", settlement.taskId());
        values.put("executionId", settlement.executionId());
        values.put("fencingToken", settlement.fencingToken());
        values.put("userId", settlement.userId());
        values.put("modelId", settlement.model().getId());
        values.put("capability", settlement.capability());
        values.put("quotaType", quotaType);
        values.put("costYuan", costYuan);
        values.put("creditCost", creditCost);
        values.put("usage", usageJson);
        values.put("rawUsage", rawUsageJson);
        values.put("occurredAt", settlement.occurredAt().toString());
        return sha256(JsonUtils.toJsonString(values));
    }

    private String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("运行环境缺少 SHA-256", failure);
        }
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

        // 3D 任务按 source+textureQuality 矩阵定价，优先于 quotaType
        if (usage instanceof Model3dGenerationService.Model3dTaskResult r3d) {
            String src = r3d.source() != null ? r3d.source() : "text";
            String tex = r3d.textureQuality() != null ? r3d.textureQuality() : "none";
            double yuan = Model3dGenerationService.lookupPrice(model, src, tex);
            return new long[] {
                Math.max(1, Math.round(yuan * YUAN_TO_CREDIT * markup)),
                Double.doubleToLongBits(yuan)
            };
        }

        // 视频任务按 resolution+duration 精确定价，禁止缺失分辨率时回退其他价格。
        if (usage instanceof VideoTaskResult vtr && vtr.getDuration() != null) {
            var vc = model.getVideoConfigParsed();
            int duration = Math.max(1, vtr.getDuration());
            if (vc != null && vc.pricing() != null && !vc.pricing().isEmpty()) {
                var resolution = Objects.requireNonNull(vtr.getResolution(), "视频实际用量缺少 resolution");
                double pricePerSec =
                        vc.pricing().stream()
                                .filter(item -> resolution.equalsIgnoreCase(item.resolution()))
                                .mapToDouble(item -> item.pricePerSecond().doubleValue())
                                .findFirst()
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        "模型缺少视频分辨率价格: " + resolution));
                double yuan = pricePerSec * duration;
                return new long[] {
                    Math.max(1, Math.round(yuan * YUAN_TO_CREDIT * markup)),
                    Double.doubleToLongBits(yuan)
                };
            }
        }

        var type = AiQuotaTypeEnum.of(quotaType);
        return switch (type) {
            case PER_USE -> {
                var modelPrice =
                        Objects.requireNonNull(
                                model.getModelPrice(), "模型缺少 PER_USE 价格: " + model.getId());
                long cost = AiCreditGuard.calcPerUseCost(modelPrice, markup);
                yield new long[] {cost, Double.doubleToLongBits(modelPrice.doubleValue())};
            }
            case PER_UNIT -> {
                int unitCount = usage.count();
                double unitPrice =
                        Objects.requireNonNull(
                                        model.getModelPrice(), "模型缺少 PER_UNIT 价格: " + model.getId())
                                .doubleValue();
                double yuan = unitPrice * unitCount;
                yield new long[] {
                    Math.max(1, Math.round(yuan * YUAN_TO_CREDIT * markup)),
                    Double.doubleToLongBits(yuan)
                };
            }
            case PER_SEC -> {
                int duration = Math.max(1, usage.duration());
                double pricePerSec =
                        Objects.requireNonNull(
                                        model.getModelPrice(), "模型缺少 PER_SEC 价格: " + model.getId())
                                .doubleValue();
                double yuan = pricePerSec * duration;
                yield new long[] {
                    Math.max(1, Math.round(yuan * YUAN_TO_CREDIT * markup)),
                    Double.doubleToLongBits(yuan)
                };
            }
            default -> {
                long input = usage.inputTokens();
                long output = usage.outputTokens();
                double inputPrice =
                        Objects.requireNonNull(
                                        model.getInputPricePerK(),
                                        "模型缺少 input_price_per_k: " + model.getId())
                                .doubleValue();
                double outputPrice =
                        Objects.requireNonNull(
                                        model.getOutputPricePerK(),
                                        "模型缺少 output_price_per_k: " + model.getId())
                                .doubleValue();
                double yuan = (input * inputPrice + output * outputPrice) / PER_K_TOKENS;
                yield new long[] {
                    Math.max(1, Math.round(yuan * YUAN_TO_CREDIT * markup)),
                    Double.doubleToLongBits(yuan)
                };
            }
        };
    }

    /** 扣积分，返回流水 ID；失败返回 null 并记录 warn。 */
    private Long doSpend(Long userId, long creditCost, String category, String remark) {
        try {
            long overdraft = configService.getInteger(SysConfigKeys.Ai.CREDIT_OVERDRAFT_LIMIT, 0);
            return creditService.spend(
                    userId,
                    creditCost,
                    CreditTransactionSourceEnum.AI_CONSUME.getCode(),
                    category,
                    null,
                    overdraft,
                    remark,
                    com.xuejiai.aaf.common.enums.pay.CreditBizTypeEnum.AI_USAGE.getCode());
        } catch (Exception e) {
            log.warn(
                    "AI 积分扣减失败: userId={}, category={}, cost={}, err={}",
                    userId,
                    category,
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
            record.setFencingToken(0L);
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
}
