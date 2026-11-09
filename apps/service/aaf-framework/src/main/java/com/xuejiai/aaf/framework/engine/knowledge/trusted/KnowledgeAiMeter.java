package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.engine.knowledge.graph.ExtractedTriple;
import com.xuejiai.aaf.framework.engine.knowledge.summary.KnowledgeSummaryPrompt.DocumentSummaryOutput;
import com.xuejiai.aaf.framework.intelligent.ai.chat.ModelThrottledException;
import com.xuejiai.aaf.framework.intelligent.core.AiUsage;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelManagementService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 抽取和消歧共享的知识 AI 计量器。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeAiMeter {

    /** 限频最多重试次数。 */
    private static final int MAX_THROTTLE_RETRIES = 5;

    /** 限频首次退避时长，之后每次翻倍。 */
    private static final long INITIAL_THROTTLE_BACKOFF_MILLIS = 2_000;

    private final KnowledgeUsagePort usagePort;
    private final ModelManagementService modelManagementService;

    public List<ExtractedTriple> invokeEntityExtraction(
            KnowledgeUsagePort.BillingContext billing,
            String stage,
            String unitKey,
            String modelId,
            String input,
            Supplier<ResponseEntity<ChatResponse, List<ExtractedTriple>>> invocation) {
        var inputDigest = inputDigest(modelId, input);
        var stored = usagePort.findProviderResult(billing, stage, unitKey, inputDigest);
        if (stored.isPresent()) {
            return settleStoredEntityExtraction(billing, stage, unitKey, modelId, stored.get());
        }

        usagePort.precheck(billing, stage, AiCreditGuard.INESTIMABLE_COST);
        var reservation = usagePort.reserve(billing, stage, unitKey, inputDigest);
        return switch (reservation.state()) {
            case RECOVERABLE, COMPLETED -> settleEntityExtractionReservation(reservation, modelId);
            case BUSY -> throw new IllegalStateException("知识入库 AI 单元正由其他调用者处理");
            case UNKNOWN ->
                    throw new IllegalStateException("知识入库 AI 调用结果未知，需按 provider request id 人工确认");
            case RESERVED -> invokeReservedEntityExtraction(reservation, modelId, invocation);
        };
    }

    /** 文档摘要阶段：与实体抽取相同的预留、恢复与结算流程，记给入库代际的付费用户。 */
    public DocumentSummaryOutput invokeDocumentSummary(
            KnowledgeUsagePort.BillingContext billing,
            String stage,
            String unitKey,
            String modelId,
            String input,
            Supplier<ResponseEntity<ChatResponse, DocumentSummaryOutput>> invocation) {
        var inputDigest = inputDigest(modelId, input);
        var stored = usagePort.findProviderResult(billing, stage, unitKey, inputDigest);
        if (stored.isPresent()) {
            return settleStoredDocumentSummary(billing, stage, unitKey, modelId, stored.get());
        }

        usagePort.precheck(billing, stage, AiCreditGuard.INESTIMABLE_COST);
        var reservation = usagePort.reserve(billing, stage, unitKey, inputDigest);
        return switch (reservation.state()) {
            case RECOVERABLE, COMPLETED -> settleDocumentSummaryReservation(reservation, modelId);
            case BUSY -> throw new IllegalStateException("知识入库 AI 单元正由其他调用者处理");
            case UNKNOWN ->
                    throw new IllegalStateException("知识入库 AI 调用结果未知，需按 provider request id 人工确认");
            case RESERVED -> invokeReservedDocumentSummary(reservation, modelId, invocation);
        };
    }

    private DocumentSummaryOutput invokeReservedDocumentSummary(
            KnowledgeUsagePort.InvocationReservation reservation,
            String modelId,
            Supplier<ResponseEntity<ChatResponse, DocumentSummaryOutput>> invocation) {
        if (!usagePort.start(reservation)) {
            throw new IllegalStateException("知识入库 AI 单元并发占用失败");
        }
        try {
            var responseEntity = callWithThrottleRetry(invocation);
            if (responseEntity == null
                    || responseEntity.response() == null
                    || responseEntity.entity() == null) {
                throw new IllegalStateException("文档摘要 AI 返回空结构化响应");
            }
            var metadata = responseEntity.response().getMetadata();
            var providerUsage = metadata == null ? null : metadata.getUsage();
            var provider =
                    new DocumentSummaryProviderResult(
                            responseEntity.entity(),
                            JsonUtils.toJsonString(responseEntity.response()),
                            providerUsage == null ? 0 : providerUsage.getPromptTokens(),
                            providerUsage == null ? 0 : providerUsage.getCompletionTokens());
            var providerJson = JsonUtils.toJsonString(provider);
            usagePort.persistProviderResult(
                    reservation, providerJson, reservation.providerRequestId());
            settle(
                    reservation,
                    modelId,
                    providerJson,
                    provider.promptTokens(),
                    provider.completionTokens());
            return provider.entity();
        } catch (ModelThrottledException throttled) {
            // 限频重试用尽：请求未被执行，退回待处理，下次入库可直接重新占用，不标记为结果未知
            usagePort.releaseForRetry(reservation, throttled.getMessage());
            throw throttled;
        } catch (RuntimeException failure) {
            usagePort.markUnknown(reservation, failure.getMessage());
            throw failure;
        }
    }

    private DocumentSummaryOutput settleStoredDocumentSummary(
            KnowledgeUsagePort.BillingContext billing,
            String stage,
            String unitKey,
            String modelId,
            KnowledgeUsagePort.StoredProviderResult stored) {
        var provider =
                JsonUtils.parseObject(stored.providerResult(), DocumentSummaryProviderResult.class);
        var reservation = reservation(billing, stage, unitKey, stored);
        settle(
                reservation,
                modelId,
                stored.providerResult(),
                provider.promptTokens(),
                provider.completionTokens());
        return provider.entity();
    }

    private DocumentSummaryOutput settleDocumentSummaryReservation(
            KnowledgeUsagePort.InvocationReservation reservation, String modelId) {
        if (reservation.providerResult() == null) {
            throw new IllegalStateException("知识入库 AI 恢复单元缺少 provider 结果");
        }
        var provider =
                JsonUtils.parseObject(
                        reservation.providerResult(), DocumentSummaryProviderResult.class);
        settle(
                reservation,
                modelId,
                reservation.providerResult(),
                provider.promptTokens(),
                provider.completionTokens());
        return provider.entity();
    }

    public String invokeText(
            KnowledgeUsagePort.BillingContext billing,
            String stage,
            String unitKey,
            String modelId,
            String input,
            Supplier<ChatResponse> invocation) {
        var inputDigest = inputDigest(modelId, input);
        var stored = usagePort.findProviderResult(billing, stage, unitKey, inputDigest);
        if (stored.isPresent()) {
            return settleStoredText(billing, stage, unitKey, modelId, stored.get());
        }

        usagePort.precheck(billing, stage, AiCreditGuard.INESTIMABLE_COST);
        var reservation = usagePort.reserve(billing, stage, unitKey, inputDigest);
        return switch (reservation.state()) {
            case RECOVERABLE, COMPLETED -> settleTextReservation(reservation, modelId);
            case BUSY -> throw new IllegalStateException("知识入库 AI 单元正由其他调用者处理");
            case UNKNOWN ->
                    throw new IllegalStateException("知识入库 AI 调用结果未知，需按 provider request id 人工确认");
            case RESERVED -> invokeReservedText(reservation, modelId, invocation);
        };
    }

    public EntityResolutionBatchDecision invokeEntityResolution(
            KnowledgeUsagePort.BillingContext billing,
            String stage,
            String unitKey,
            String modelId,
            String input,
            int maxResponseLength,
            Supplier<ResponseEntity<ChatResponse, EntityResolutionBatchDecision>> invocation) {
        var inputDigest = inputDigest(modelId, input);
        var stored = usagePort.findProviderResult(billing, stage, unitKey, inputDigest);
        if (stored.isPresent()) {
            return settleStoredEntityResolution(billing, stage, unitKey, modelId, stored.get());
        }

        usagePort.precheck(billing, stage, AiCreditGuard.INESTIMABLE_COST);
        var reservation = usagePort.reserve(billing, stage, unitKey, inputDigest);
        return switch (reservation.state()) {
            case RECOVERABLE, COMPLETED -> settleEntityResolutionReservation(reservation, modelId);
            case BUSY -> throw new IllegalStateException("知识入库 AI 单元正由其他调用者处理");
            case UNKNOWN ->
                    throw new IllegalStateException("知识入库 AI 调用结果未知，需按 provider request id 人工确认");
            case RESERVED ->
                    invokeReservedEntityResolution(
                            reservation, modelId, maxResponseLength, invocation);
        };
    }

    private EntityResolutionBatchDecision invokeReservedEntityResolution(
            KnowledgeUsagePort.InvocationReservation reservation,
            String modelId,
            int maxResponseLength,
            Supplier<ResponseEntity<ChatResponse, EntityResolutionBatchDecision>> invocation) {
        if (!usagePort.start(reservation)) {
            throw new IllegalStateException("知识入库 AI 单元并发占用失败");
        }
        try {
            var responseEntity = callWithThrottleRetry(invocation);
            if (responseEntity == null
                    || responseEntity.response() == null
                    || responseEntity.entity() == null) {
                throw new IllegalStateException("实体消歧 AI 返回空结构化响应");
            }
            var response = responseEntity.response();
            // 结构化结果来自工具调用入参，正文为空；按结构化结果序列化长度限制
            if (JsonUtils.toJsonString(responseEntity.entity()).length() > maxResponseLength) {
                throw new IllegalStateException("实体消歧结果超过最大长度");
            }
            var metadata = response.getMetadata();
            var providerUsage = metadata == null ? null : metadata.getUsage();
            var provider =
                    new EntityResolutionProviderResult(
                            responseEntity.entity(),
                            JsonUtils.toJsonString(response),
                            providerUsage == null ? 0 : providerUsage.getPromptTokens(),
                            providerUsage == null ? 0 : providerUsage.getCompletionTokens());
            var providerJson = JsonUtils.toJsonString(provider);
            usagePort.persistProviderResult(
                    reservation, providerJson, reservation.providerRequestId());
            settle(
                    reservation,
                    modelId,
                    providerJson,
                    provider.promptTokens(),
                    provider.completionTokens());
            return provider.entity();
        } catch (ModelThrottledException throttled) {
            // 限频重试用尽：请求未被执行，退回待处理，下次入库可直接重新占用，不标记为结果未知
            usagePort.releaseForRetry(reservation, throttled.getMessage());
            throw throttled;
        } catch (RuntimeException failure) {
            usagePort.markUnknown(reservation, failure.getMessage());
            throw failure;
        }
    }

    private EntityResolutionBatchDecision settleStoredEntityResolution(
            KnowledgeUsagePort.BillingContext billing,
            String stage,
            String unitKey,
            String modelId,
            KnowledgeUsagePort.StoredProviderResult stored) {
        var provider =
                JsonUtils.parseObject(
                        stored.providerResult(), EntityResolutionProviderResult.class);
        var reservation = reservation(billing, stage, unitKey, stored);
        settle(
                reservation,
                modelId,
                stored.providerResult(),
                provider.promptTokens(),
                provider.completionTokens());
        return provider.entity();
    }

    private EntityResolutionBatchDecision settleEntityResolutionReservation(
            KnowledgeUsagePort.InvocationReservation reservation, String modelId) {
        if (reservation.providerResult() == null) {
            throw new IllegalStateException("知识入库 AI 恢复单元缺少 provider 结果");
        }
        var provider =
                JsonUtils.parseObject(
                        reservation.providerResult(), EntityResolutionProviderResult.class);
        settle(
                reservation,
                modelId,
                reservation.providerResult(),
                provider.promptTokens(),
                provider.completionTokens());
        return provider.entity();
    }

    /**
     * 执行模型调用，遇到供应商限频时按指数退避加随机抖动重试（2、4、8、16、32 秒）。
     *
     * <p>只重试限频：限频请求未被执行，重试不会重复计费；超时与服务端错误可能已执行，仍交由调用方按结果未知处理。 最长等待约 93 秒，在单元 {@code IN_PROGRESS}
     * 租约内。等待中被中断时恢复中断标记并抛出原限频异常。
     */
    private <T> T callWithThrottleRetry(Supplier<T> invocation) {
        var backoffMillis = INITIAL_THROTTLE_BACKOFF_MILLIS;
        for (var attempt = 0; ; attempt++) {
            try {
                return invocation.get();
            } catch (ModelThrottledException throttled) {
                if (attempt >= MAX_THROTTLE_RETRIES) {
                    throw throttled;
                }
                var delay =
                        backoffMillis + ThreadLocalRandom.current().nextLong(backoffMillis / 2 + 1);
                log.warn(
                        "知识入库 AI 调用被限频，{}ms 后第 {} 次重试: {}",
                        delay,
                        attempt + 1,
                        throttled.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw throttled;
                }
                backoffMillis *= 2;
            }
        }
    }

    private String inputDigest(String modelId, String input) {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("知识入库 AI modelId 不能为空");
        }
        return TrustedKnowledgeStore.sha256(modelId + "|" + input);
    }

    private List<ExtractedTriple> invokeReservedEntityExtraction(
            KnowledgeUsagePort.InvocationReservation reservation,
            String modelId,
            Supplier<ResponseEntity<ChatResponse, List<ExtractedTriple>>> invocation) {
        if (!usagePort.start(reservation)) {
            throw new IllegalStateException("知识入库 AI 单元并发占用失败");
        }
        try {
            var responseEntity = callWithThrottleRetry(invocation);
            if (responseEntity == null
                    || responseEntity.response() == null
                    || responseEntity.entity() == null) {
                throw new IllegalStateException("知识入库 AI 返回空结构化响应");
            }
            var metadata = responseEntity.response().getMetadata();
            var providerUsage = metadata == null ? null : metadata.getUsage();
            var provider =
                    new EntityExtractionProviderResult(
                            responseEntity.entity(),
                            JsonUtils.toJsonString(responseEntity.response()),
                            providerUsage == null ? 0 : providerUsage.getPromptTokens(),
                            providerUsage == null ? 0 : providerUsage.getCompletionTokens());
            var providerJson = JsonUtils.toJsonString(provider);
            usagePort.persistProviderResult(
                    reservation, providerJson, reservation.providerRequestId());
            settle(
                    reservation,
                    modelId,
                    providerJson,
                    provider.promptTokens(),
                    provider.completionTokens());
            return provider.entity();
        } catch (ModelThrottledException throttled) {
            // 限频重试用尽：请求未被执行，退回待处理，下次入库可直接重新占用，不标记为结果未知
            usagePort.releaseForRetry(reservation, throttled.getMessage());
            throw throttled;
        } catch (RuntimeException failure) {
            usagePort.markUnknown(reservation, failure.getMessage());
            throw failure;
        }
    }

    private String invokeReservedText(
            KnowledgeUsagePort.InvocationReservation reservation,
            String modelId,
            Supplier<ChatResponse> invocation) {
        if (!usagePort.start(reservation)) {
            throw new IllegalStateException("知识入库 AI 单元并发占用失败");
        }
        try {
            var response = callWithThrottleRetry(invocation);
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
            settle(
                    reservation,
                    modelId,
                    providerJson,
                    provider.promptTokens(),
                    provider.completionTokens());
            return provider.text();
        } catch (ModelThrottledException throttled) {
            // 限频重试用尽：请求未被执行，退回待处理，下次入库可直接重新占用，不标记为结果未知
            usagePort.releaseForRetry(reservation, throttled.getMessage());
            throw throttled;
        } catch (RuntimeException failure) {
            usagePort.markUnknown(reservation, failure.getMessage());
            throw failure;
        }
    }

    private List<ExtractedTriple> settleStoredEntityExtraction(
            KnowledgeUsagePort.BillingContext billing,
            String stage,
            String unitKey,
            String modelId,
            KnowledgeUsagePort.StoredProviderResult stored) {
        var provider =
                JsonUtils.parseObject(
                        stored.providerResult(), EntityExtractionProviderResult.class);
        var reservation = reservation(billing, stage, unitKey, stored);
        settle(
                reservation,
                modelId,
                stored.providerResult(),
                provider.promptTokens(),
                provider.completionTokens());
        return provider.entity();
    }

    private String settleStoredText(
            KnowledgeUsagePort.BillingContext billing,
            String stage,
            String unitKey,
            String modelId,
            KnowledgeUsagePort.StoredProviderResult stored) {
        var provider = JsonUtils.parseObject(stored.providerResult(), ChatProviderResult.class);
        var reservation = reservation(billing, stage, unitKey, stored);
        settle(
                reservation,
                modelId,
                stored.providerResult(),
                provider.promptTokens(),
                provider.completionTokens());
        return provider.text();
    }

    private List<ExtractedTriple> settleEntityExtractionReservation(
            KnowledgeUsagePort.InvocationReservation reservation, String modelId) {
        if (reservation.providerResult() == null) {
            throw new IllegalStateException("知识入库 AI 恢复单元缺少 provider 结果");
        }
        var provider =
                JsonUtils.parseObject(
                        reservation.providerResult(), EntityExtractionProviderResult.class);
        settle(
                reservation,
                modelId,
                reservation.providerResult(),
                provider.promptTokens(),
                provider.completionTokens());
        return provider.entity();
    }

    private String settleTextReservation(
            KnowledgeUsagePort.InvocationReservation reservation, String modelId) {
        if (reservation.providerResult() == null) {
            throw new IllegalStateException("知识入库 AI 恢复单元缺少 provider 结果");
        }
        var provider =
                JsonUtils.parseObject(reservation.providerResult(), ChatProviderResult.class);
        settle(
                reservation,
                modelId,
                reservation.providerResult(),
                provider.promptTokens(),
                provider.completionTokens());
        return provider.text();
    }

    private KnowledgeUsagePort.InvocationReservation reservation(
            KnowledgeUsagePort.BillingContext billing,
            String stage,
            String unitKey,
            KnowledgeUsagePort.StoredProviderResult stored) {
        return new KnowledgeUsagePort.InvocationReservation(
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
    }

    private void settle(
            KnowledgeUsagePort.InvocationReservation reservation,
            String modelId,
            String providerResult,
            long promptTokens,
            long completionTokens) {
        var model = modelManagementService.getModel(modelId);
        var usage = new ChatTokenUsage(promptTokens, completionTokens);
        var cost =
                price(model.getInputPricePerK(), promptTokens)
                        .add(price(model.getOutputPricePerK(), completionTokens));
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

    private record EntityExtractionProviderResult(
            List<ExtractedTriple> entity,
            String rawResponse,
            long promptTokens,
            long completionTokens) {
        private EntityExtractionProviderResult {
            entity = List.copyOf(entity);
        }
    }

    /** 批量消歧工具入参：每个输入 key 一条决策。 */
    public record EntityResolutionBatchDecision(List<EntityResolutionDecision> decisions) {}

    public record EntityResolutionDecision(String key, String action, String entityId) {}

    private record EntityResolutionProviderResult(
            EntityResolutionBatchDecision entity,
            String rawResponse,
            long promptTokens,
            long completionTokens) {}

    private record ChatProviderResult(String text, long promptTokens, long completionTokens) {}

    private record DocumentSummaryProviderResult(
            DocumentSummaryOutput entity,
            String rawResponse,
            long promptTokens,
            long completionTokens) {}

    private record ChatTokenUsage(long inputTokens, long outputTokens) implements AiUsage {
        @Override
        public Map<String, Object> standardUsage() {
            return Map.of("inputTokens", inputTokens, "outputTokens", outputTokens);
        }
    }
}
