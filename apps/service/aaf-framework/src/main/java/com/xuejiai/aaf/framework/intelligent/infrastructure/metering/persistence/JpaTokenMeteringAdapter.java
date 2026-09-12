package com.xuejiai.aaf.framework.intelligent.infrastructure.metering.persistence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard.IdempotentUsageSettlement;
import com.xuejiai.aaf.framework.intelligent.agent.port.TokenMeteringPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskUnitOfWork;
import com.xuejiai.aaf.framework.intelligent.core.AiUsage;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelManagementService;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;

/** 将真实模型价格映射为 Credit 单账本幂等结算。 */
public final class JpaTokenMeteringAdapter implements TokenMeteringPort {
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private final ModelManagementService models;
    private final AiCreditGuard creditGuard;
    private final TaskUnitOfWork delegatedTasks;
    private final ConversationLeasePort leases;

    public JpaTokenMeteringAdapter(
            ModelManagementService models,
            AiCreditGuard creditGuard,
            TaskUnitOfWork delegatedTasks,
            ConversationLeasePort leases) {
        this.models = Objects.requireNonNull(models, "models 不能为空");
        this.creditGuard = Objects.requireNonNull(creditGuard, "creditGuard 不能为空");
        this.delegatedTasks = Objects.requireNonNull(delegatedTasks, "delegatedTasks 不能为空");
        this.leases = Objects.requireNonNull(leases, "leases 不能为空");
    }

    @Override
    public MeteringResult record(ModelUsageFact fact) {
        var userId = parsePositiveLong(fact.context().userId().value(), "userId");
        var databaseModelId = parsePositiveLong(fact.modelId(), "modelId");
        var model = models.getModel(databaseModelId);
        if (!model.hasCapability(fact.capability())) {
            throw new IllegalStateException(
                    "模型能力映射缺失: " + databaseModelId + '/' + fact.capability());
        }
        if (model.getQuotaType() == null || model.getQuotaType() != 0) {
            throw new IllegalStateException("Token 用量只能映射 TOKEN 定价模型: " + databaseModelId);
        }
        var inputPrice =
                Objects.requireNonNull(
                        model.getInputPricePerK(), "模型缺少 input_price_per_k: " + databaseModelId);
        var outputPrice =
                Objects.requireNonNull(
                        model.getOutputPricePerK(), "模型缺少 output_price_per_k: " + databaseModelId);
        var cacheRatio =
                fact.cachedTokens() == 0
                        ? BigDecimal.ZERO
                        : Objects.requireNonNull(
                                model.getCacheRatio(),
                                "模型存在缓存用量但缺少 cache_ratio: " + databaseModelId);
        var cost = tokenCost(fact, inputPrice, outputPrice, cacheRatio);
        if (fact.context().controlMode() == ControlMode.DELEGATED) {
            delegatedTasks.recordModelUsage(
                    fact.context(),
                    fact.inputTokens() + fact.outputTokens(),
                    cost,
                    fact.occurredAt());
        }
        var usage =
                new TokenUsage(
                        fact.inputTokens(),
                        fact.outputTokens(),
                        fact.cachedTokens(),
                        inputPrice,
                        outputPrice,
                        cacheRatio);
        if (fact.context().controlMode() == ControlMode.DELEGATED) {
            leases.requireCurrent(fact.context().lease());
            delegatedTasks.requireAgentExecution(fact.context());
        }
        var result =
                creditGuard.settleIdempotently(
                        new IdempotentUsageSettlement(
                                fact.usageId(),
                                fact.context().tenantId().value(),
                                fact.context().taskId() == null
                                        ? fact.context().executionId().value()
                                        : fact.context().taskId().value(),
                                fact.context().executionId().value(),
                                fact.context().lease() == null
                                        ? 0L
                                        : fact.context().lease().fencingToken(),
                                userId,
                                model,
                                usage,
                                fact.capability(),
                                cost,
                                fact.occurredAt(),
                                "Agent 执行 Token 用量"));
        return new MeteringResult(result.usageKey(), result.created());
    }

    private BigDecimal tokenCost(
            ModelUsageFact fact,
            BigDecimal inputPrice,
            BigDecimal outputPrice,
            BigDecimal cacheRatio) {
        var billableInput = fact.inputTokens() - fact.cachedTokens();
        var inputCost = inputPrice.multiply(BigDecimal.valueOf(billableInput));
        if (fact.cachedTokens() > 0) {
            inputCost =
                    inputCost.add(
                            inputPrice
                                    .multiply(cacheRatio)
                                    .multiply(BigDecimal.valueOf(fact.cachedTokens())));
        }
        return inputCost
                .add(outputPrice.multiply(BigDecimal.valueOf(fact.outputTokens())))
                .divide(THOUSAND, 6, RoundingMode.HALF_UP);
    }

    private Long parsePositiveLong(String value, String field) {
        final long parsed;
        try {
            parsed = Long.parseLong(value);
        } catch (NumberFormatException failure) {
            throw new IllegalStateException(field + " 无法映射真实 Long 标识: " + value, failure);
        }
        if (parsed <= 0) {
            throw new IllegalStateException(field + " 必须映射为正 Long: " + value);
        }
        return parsed;
    }

    private record TokenUsage(
            long inputTokens,
            long outputTokens,
            long cachedTokens,
            BigDecimal inputPricePerK,
            BigDecimal outputPricePerK,
            BigDecimal cacheRatio)
            implements AiUsage {

        @Override
        public Map<String, Object> standardUsage() {
            var values = new LinkedHashMap<String, Object>();
            values.put("inputTokens", inputTokens);
            values.put("outputTokens", outputTokens);
            values.put("cachedTokens", cachedTokens);
            values.put("inputPricePerK", inputPricePerK);
            values.put("outputPricePerK", outputPricePerK);
            values.put("cacheRatio", cacheRatio);
            return Collections.unmodifiableMap(values);
        }
    }
}
