package com.xuejiai.aaf.framework.engine.credit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import com.xuejiai.aaf.common.exception.InsufficientCreditsException;
import com.xuejiai.aaf.framework.intelligent.core.AiUsage;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;

/** AI 能力调用积分门控接口。 */
public interface AiCreditGuard {

    /** 1元 = 100积分（积分单位为“分”）。所有计费计算统一引用此常量。 */
    double YUAN_TO_CREDIT = 100.0;

    /** 传入 precheck 的预估值：表示无法估算，降级为余额 > 0 保守检查。 */
    long INESTIMABLE_COST = 0L;

    /** 计算按次/按单位积分费用。 */
    static long calcPerUseCost(BigDecimal modelPrice, int markupRate) {
        Objects.requireNonNull(modelPrice, "模型缺少固定价格");
        return Math.max(1, Math.round(modelPrice.doubleValue() * YUAN_TO_CREDIT * markupRate));
    }

    /** 获取当前积分倍率。 */
    default int getMarkupRate() {
        return 5;
    }

    boolean hasBudget(Long userId, long estimatedCost);

    void precheck(Long userId, String capability, long estimatedCost)
            throws InsufficientCreditsException;

    default void settleFixed(Long userId, long creditCost, String capability) {
        // 实现类按需覆写。
    }

    /**
     * 按固定单价结算并返回积分流水 ID（用于非模型驱动的固定计费场景，如图像分割）。
     *
     * @return 扣减成功的积分流水 ID；未覆写或扣减失败返回 null
     */
    default Long settleFixedReturningTxId(
            Long userId, long creditCost, String capability, String remark) {
        settleFixed(userId, creditCost, capability);
        return null;
    }

    /** 按真实模型与用量结算。model 不能为空。 */
    void settleByUsage(Long userId, AiModel model, AiUsage usage, String capability, String remark);

    /** 按真实模型与用量结算并返回积分流水 ID。 */
    default Long settleByUsageReturningTxId(
            Long userId, AiModel model, AiUsage usage, String capability, String remark) {
        settleByUsage(userId, model, usage, capability, remark);
        return null;
    }

    /**
     * 对稳定 usageKey 执行真实积分扣减与 ai_usage_record 单事实写入。
     *
     * <p>同键同内容返回原流水；同键不同内容拒绝；扣减与用量记录必须处于同一事务。
     */
    IdempotentSettlementResult settleIdempotently(IdempotentUsageSettlement settlement);

    record IdempotentUsageSettlement(
            String usageKey,
            String tenantId,
            String taskId,
            String executionId,
            long fencingToken,
            Long userId,
            AiModel model,
            AiUsage usage,
            String capability,
            BigDecimal costYuan,
            Instant occurredAt,
            String remark) {
        public IdempotentUsageSettlement {
            usageKey = requireText(usageKey, "usageKey");
            tenantId = requireText(tenantId, "tenantId");
            taskId = requireText(taskId, "taskId");
            executionId = requireText(executionId, "executionId");
            if (fencingToken < 0) {
                throw new IllegalArgumentException("fencingToken 不能为负数");
            }
            Objects.requireNonNull(userId, "userId 不能为空");
            if (userId <= 0) {
                throw new IllegalArgumentException("userId 必须大于 0");
            }
            Objects.requireNonNull(model, "model 不能为空");
            Objects.requireNonNull(model.getId(), "model.id 不能为空");
            Objects.requireNonNull(usage, "usage 不能为空");
            capability = requireText(capability, "capability");
            Objects.requireNonNull(costYuan, "costYuan 不能为空");
            if (costYuan.signum() < 0) {
                throw new IllegalArgumentException("costYuan 不能为负数");
            }
            Objects.requireNonNull(occurredAt, "occurredAt 不能为空");
            remark = Objects.requireNonNullElse(remark, "AI 用量结算");
        }

        private static String requireText(String value, String name) {
            Objects.requireNonNull(value, name + " 不能为空");
            if (value.isBlank()) {
                throw new IllegalArgumentException(name + " 不能为空白");
            }
            return value.trim();
        }
    }

    record IdempotentSettlementResult(String usageKey, Long creditTxId, boolean created) {
        public IdempotentSettlementResult {
            Objects.requireNonNull(usageKey, "usageKey 不能为空");
            Objects.requireNonNull(creditTxId, "creditTxId 不能为空");
        }
    }

    Long refund(Long creditTxId, String reason);
}
