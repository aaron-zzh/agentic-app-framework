package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.core.AiUsage;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelManagementService;

import lombok.RequiredArgsConstructor;

/** 抽取和消歧共享的知识 AI 计量器。 */
@Component
@RequiredArgsConstructor
public class KnowledgeAiMeter {

    private final KnowledgeUsagePort usagePort;
    private final ModelManagementService modelManagementService;

    public String invokeText(
            KnowledgeUsagePort.BillingContext billing,
            String stage,
            String unitKey,
            String modelId,
            String input,
            Supplier<ChatResponse> invocation) {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("知识入库 AI modelId 不能为空");
        }
        var inputDigest = TrustedKnowledgeStore.sha256(modelId + "|" + input);
        var stored = usagePort.findProviderResult(billing, stage, unitKey, inputDigest);
        if (stored.isPresent()) {
            return settleStored(billing, stage, unitKey, modelId, stored.get());
        }

        usagePort.precheck(billing, stage, AiCreditGuard.INESTIMABLE_COST);
        var reservation = usagePort.reserve(billing, stage, unitKey, inputDigest);
        return switch (reservation.state()) {
            case RECOVERABLE, COMPLETED -> settleReservation(reservation, modelId);
            case BUSY -> throw new IllegalStateException("知识入库 AI 单元正由其他调用者处理");
            case UNKNOWN ->
                    throw new IllegalStateException("知识入库 AI 调用结果未知，需按 provider request id 人工确认");
            case RESERVED -> invokeReserved(reservation, modelId, invocation);
        };
    }

    private String invokeReserved(
            KnowledgeUsagePort.InvocationReservation reservation,
            String modelId,
            Supplier<ChatResponse> invocation) {
        if (!usagePort.start(reservation)) {
            throw new IllegalStateException("知识入库 AI 单元并发占用失败");
        }
        try {
            var response = invocation.get();
            if (response == null || response.getResult() == null) {
                throw new IllegalStateException("知识入库 AI 返回空响应");
            }
            var metadata = response.getMetadata();
            var providerUsage = metadata == null ? null : metadata.getUsage();
            var provider =
                    new ChatProviderResult(
                            response.getResult().getOutput().getText(),
                            providerUsage == null ? 0 : providerUsage.getPromptTokens(),
                            providerUsage == null ? 0 : providerUsage.getCompletionTokens());
            var providerJson = JsonUtils.toJsonString(provider);
            usagePort.persistProviderResult(
                    reservation, providerJson, reservation.providerRequestId());
            settle(reservation, modelId, providerJson, provider);
            return provider.text();
        } catch (RuntimeException failure) {
            usagePort.markUnknown(reservation, failure.getMessage());
            throw failure;
        }
    }

    private String settleStored(
            KnowledgeUsagePort.BillingContext billing,
            String stage,
            String unitKey,
            String modelId,
            KnowledgeUsagePort.StoredProviderResult stored) {
        var provider = JsonUtils.parseObject(stored.providerResult(), ChatProviderResult.class);
        var reservation =
                new KnowledgeUsagePort.InvocationReservation(
                        billing,
                        stage,
                        unitKey,
                        stored.inputDigest(),
                        stored.invocationId(),
                        stored.usageKey(),
                        stored.occurredAt(),
                        null,
                        stored.settled()
                                ? KnowledgeUsagePort.ReservationState.COMPLETED
                                : KnowledgeUsagePort.ReservationState.RECOVERABLE,
                        stored.providerResult(),
                        stored.providerRequestId());
        settle(reservation, modelId, stored.providerResult(), provider);
        return provider.text();
    }

    private String settleReservation(
            KnowledgeUsagePort.InvocationReservation reservation, String modelId) {
        if (reservation.providerResult() == null) {
            throw new IllegalStateException("知识入库 AI 恢复单元缺少 provider 结果");
        }
        var provider =
                JsonUtils.parseObject(reservation.providerResult(), ChatProviderResult.class);
        settle(reservation, modelId, reservation.providerResult(), provider);
        return provider.text();
    }

    private void settle(
            KnowledgeUsagePort.InvocationReservation reservation,
            String modelId,
            String providerResult,
            ChatProviderResult provider) {
        var model = modelManagementService.getModel(modelId);
        var usage = new ChatTokenUsage(provider.promptTokens(), provider.completionTokens());
        var cost =
                price(model.getInputPricePerK(), provider.promptTokens())
                        .add(price(model.getOutputPricePerK(), provider.completionTokens()));
        usagePort.settle(
                new KnowledgeUsagePort.UsageCall(
                        reservation.usageKey(),
                        reservation.billing(),
                        reservation.stage(),
                        reservation.unitKey(),
                        reservation.invocationId(),
                        reservation.inputDigest(),
                        providerResult,
                        reservation.providerRequestId(),
                        model,
                        usage,
                        cost,
                        reservation.occurredAt()));
    }

    private BigDecimal price(BigDecimal pricePerK, long tokens) {
        return (pricePerK == null ? BigDecimal.ZERO : pricePerK)
                .multiply(BigDecimal.valueOf(tokens))
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
    }

    private record ChatProviderResult(String text, long promptTokens, long completionTokens) {}

    private record ChatTokenUsage(long inputTokens, long outputTokens) implements AiUsage {
        @Override
        public Map<String, Object> standardUsage() {
            return Map.of("inputTokens", inputTokens, "outputTokens", outputTokens);
        }
    }
}
