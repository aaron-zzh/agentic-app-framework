package com.xuejiai.aaf.framework.engine.knowledge.embedding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.openai.errors.OpenAIServiceException;

import com.xuejiai.aaf.common.enums.pay.CreditTransactionCategoryEnum;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeUsagePort;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeUsagePort.BillingContext;
import com.xuejiai.aaf.framework.engine.meta.runtime.NonRetryableTaskException;
import com.xuejiai.aaf.framework.intelligent.ai.embedding.DynamicEmbeddingModelFactory;
import com.xuejiai.aaf.framework.intelligent.core.AiUsage;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;

import lombok.extern.slf4j.Slf4j;

/**
 * Embedding 生成服务，支持批量生成、缓存、重试。
 *
 * <p>M53：embedding 成本由触发用户承担（产品决策，2026-08-01）。门控与结算走 {@link AiCreditGuard}，按模型 {@code
 * input_price_per_k} 与 provider 返回的 usage.prompt_tokens 按量计费；响应缺少用量即失败。
 *
 * <p>调用方（记忆抽取/去重/检索、知识库导入适配器等）必须传入承担成本的 {@code userId}；系统内部批量任务（如离线重建索引）若无自然归属用户，
 * 由调用方自行决定记账到系统账户或拒绝匿名调用，本服务不做静默兜底。
 */
@Slf4j
@Service
@EnableConfigurationProperties(EmbeddingProperties.class)
public class EmbeddingService {

    private final CapabilityRouter capabilityRouter;
    private final DynamicEmbeddingModelFactory embeddingModels;
    private final EmbeddingProperties properties;
    private final AiCreditGuard creditGuard;
    private final KnowledgeUsagePort knowledgeUsagePort;
    // 有界缓存：每条 1536 维向量约 6KB，上限 5000 条约 30MB，按最近最少使用淘汰
    private final Cache<String, float[]> cache = Caffeine.newBuilder().maximumSize(5_000).build();

    public EmbeddingService(
            CapabilityRouter capabilityRouter,
            DynamicEmbeddingModelFactory embeddingModels,
            EmbeddingProperties properties,
            AiCreditGuard creditGuard,
            KnowledgeUsagePort knowledgeUsagePort) {
        this.capabilityRouter = capabilityRouter;
        this.embeddingModels = embeddingModels;
        this.properties = properties;
        this.creditGuard = creditGuard;
        this.knowledgeUsagePort = knowledgeUsagePort;
    }

    /** 单条文本生成 embedding，成本记账到 userId。 */
    public float[] embed(String text, Long userId) {
        Objects.requireNonNull(userId, "M53：embedding 成本需要归属用户，userId 不能为空");
        var model = resolveModel(userId);
        var key = cacheKey(model, text);
        var cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        creditGuard.precheck(
                userId,
                CreditTransactionCategoryEnum.EMBEDDING.getCode(),
                AiCreditGuard.INESTIMABLE_COST);
        var result = embedWithRetry(embeddingModelOf(model), text);
        cache.asMap().putIfAbsent(key, result.vector());
        log.info(
                "[Embedding] 向量化完成: userId={}, modelId={}, inputTokens={}",
                userId,
                model.getModelId(),
                result.inputTokens());
        creditGuard.settleByUsage(
                userId,
                model,
                new EmbeddingUsage(result.inputTokens(), 1),
                CapabilityRoutingContext.CAP_EMBEDDING,
                "Embedding 向量化");
        return result.vector();
    }

    /** 批量生成 embedding，按 batchSize 分批调用，成本记账到 userId（按模型价格按量计费，缓存命中不重复计费）。 */
    public List<float[]> embedBatch(List<String> texts, int batchSize, Long userId) {
        Objects.requireNonNull(userId, "M53：embedding 成本需要归属用户，userId 不能为空");
        var results = new ArrayList<float[]>(texts.size());
        for (int i = 0; i < texts.size(); i += batchSize) {
            var batch = texts.subList(i, Math.min(i + batchSize, texts.size()));
            for (var text : batch) {
                results.add(embed(text, userId));
            }
        }
        return results;
    }

    /**
     * 全局固定一个向量模型（{@code aaf.knowledge.embedding.model}），不支持按用户或知识库切换。
     *
     * <p>仍经 {@link CapabilityRouter} 的「显式指定」层解析，复用其启用状态与 EMBEDDING 能力校验；不走用户偏好/系统默认层，
     * 避免不同用户落到不同向量空间导致入库与检索对不上。
     */
    private AiModel resolveModel(Long userId) {
        return capabilityRouter.resolve(
                CapabilityRoutingContext.of(
                        userId, CapabilityRoutingContext.CAP_EMBEDDING, properties.model()));
    }

    private EmbeddingModel embeddingModelOf(AiModel model) {
        return embeddingModels.get(model, properties.dimensions());
    }

    /** 缓存键带模型标识：不同模型的向量空间不可混用。 */
    private String cacheKey(AiModel model, String text) {
        return model.getModelId() + "|" + sha256(text);
    }

    /** 单条文本向量化；返回向量与 provider 实际返回的输入 Token 数（用量缺失即失败，避免静默漏账）。 */
    private EmbeddedText embedWithRetry(EmbeddingModel embeddingModel, String text) {
        var response = callWithRetry(embeddingModel, List.of(text));
        return new EmbeddedText(response.getResult().getOutput(), providerInputTokens(response));
    }

    /** 多条文本一次请求向量化，按 provider 返回的 index 对齐输入顺序。 */
    private EmbeddedBatch embedBatchWithRetry(EmbeddingModel embeddingModel, List<String> texts) {
        var response = callWithRetry(embeddingModel, texts);
        var results =
                response.getResults().stream()
                        .sorted(Comparator.comparing(Embedding::getIndex))
                        .map(Embedding::getOutput)
                        .toList();
        if (results.size() != texts.size()) {
            throw new IllegalStateException(
                    "Embedding 返回条数与输入不一致: expected=%d, actual=%d"
                            .formatted(texts.size(), results.size()));
        }
        return new EmbeddedBatch(results, providerInputTokens(response));
    }

    /** 带指数退避的 embedding 调用；限流、5xx 与网络异常重试，其余 4xx 直接失败。 */
    private EmbeddingResponse callWithRetry(EmbeddingModel embeddingModel, List<String> texts) {
        for (int attempt = 1; ; attempt++) {
            try {
                return embeddingModel.call(
                        new EmbeddingRequest(texts, EmbeddingOptions.builder().build()));
            } catch (RuntimeException e) {
                var status = providerStatus(e);
                if (status != null && status != 429 && status < 500) {
                    throw new NonRetryableTaskException(
                            "Embedding 请求被拒绝（HTTP %d）：%s".formatted(status, e.getMessage()), e);
                }
                if (attempt >= properties.maxRetries()) {
                    throw new IllegalStateException(
                            "Embedding 生成失败，已重试 %d 次".formatted(properties.maxRetries()), e);
                }
                var delayMillis = backoffMillis(attempt);
                log.warn(
                        "Embedding 生成失败，{}ms 后第 {} 次重试，status={}，原因：{}",
                        delayMillis,
                        attempt + 1,
                        status,
                        e.getMessage());
                sleep(delayMillis);
            }
        }
    }

    /** 第 n 次失败后等待 1s、2s、4s…（上限 8s），叠加 0-500ms 抖动避免并发任务同时重试。 */
    private static long backoffMillis(int attempt) {
        var base = Math.min(1000L << (attempt - 1), 8000L);
        return base + ThreadLocalRandom.current().nextLong(500);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Embedding 重试被中断", ie);
        }
    }

    /** 从异常链中取 provider HTTP 状态码；网络异常、超时等无状态码时返回 null。 */
    private static Integer providerStatus(Throwable failure) {
        for (var cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof OpenAIServiceException serviceException) {
                return serviceException.statusCode();
            }
        }
        return null;
    }

    /** provider 实际返回的输入 Token 数；缺失或为 0 视为 provider 未上报，无法计费。 */
    private long providerInputTokens(EmbeddingResponse response) {
        var usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        var tokens = usage == null ? null : usage.getPromptTokens();
        if (tokens == null || tokens <= 0) {
            throw new IllegalStateException("Embedding 响应缺少 usage.prompt_tokens，无法按量计费");
        }
        return tokens;
    }

    private String sha256(String text) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(text.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 知识入库批量向量化：按 batchSize 切成子单元（unitKey 追加起始序号），每个子单元一次请求、独立预留与幂等结算；
     * 某个子单元失败只需重做该子单元，已落盘的子单元重试时直接复用。
     */
    public List<float[]> embedKnowledgeBatch(
            List<String> texts, BillingContext billing, String unitKey) {
        Objects.requireNonNull(texts, "texts 不能为空");
        Objects.requireNonNull(billing, "billing 不能为空");
        if (texts.isEmpty()) {
            return List.of();
        }
        texts.forEach(text -> Objects.requireNonNull(text, "知识入库文本不能为空"));
        var model = resolveModel(billing.payerUserId());
        var embeddingModel = embeddingModelOf(model);
        var batchSize = properties.batchSize();
        var vectors = new ArrayList<float[]>(texts.size());
        for (var start = 0; start < texts.size(); start += batchSize) {
            var batch = texts.subList(start, Math.min(start + batchSize, texts.size()));
            vectors.addAll(
                    embedKnowledgeUnit(
                            batch, billing, unitKey + ":" + start, model, embeddingModel));
        }
        log.info(
                "[Embedding] 知识入库向量化完成: payerUserId={}, modelId={}, unitKey={}, count={}, units={}",
                billing.payerUserId(),
                model.getModelId(),
                unitKey,
                vectors.size(),
                (texts.size() + batchSize - 1) / batchSize);
        return List.copyOf(vectors);
    }

    private List<float[]> embedKnowledgeUnit(
            List<String> texts,
            BillingContext billing,
            String unitKey,
            AiModel model,
            EmbeddingModel embeddingModel) {
        var inputDigest =
                sha256(
                        model.getModelId()
                                + "|"
                                + String.join("|", texts.stream().map(this::sha256).toList()));
        var stored =
                knowledgeUsagePort.findProviderResult(billing, "embedding", unitKey, inputDigest);
        if (stored.isPresent()) {
            var provider =
                    com.xuejiai.aaf.common.util.JsonUtils.parseObject(
                            stored.get().providerResult(), EmbeddingProviderResult.class);
            settleKnowledgeEmbedding(
                    reservation(billing, unitKey, stored.get()),
                    stored.get().providerResult(),
                    model,
                    provider);
            cacheSettled(model, texts, provider.vectors());
            return List.copyOf(provider.vectors());
        }

        knowledgeUsagePort.precheck(
                billing,
                CreditTransactionCategoryEnum.EMBEDDING.getCode(),
                AiCreditGuard.INESTIMABLE_COST);
        var reserved = knowledgeUsagePort.reserve(billing, "embedding", unitKey, inputDigest);
        if (reserved.state() == KnowledgeUsagePort.ReservationState.BUSY) {
            throw new IllegalStateException("知识入库 embedding 单元正由其他调用者处理");
        }
        if (reserved.state() == KnowledgeUsagePort.ReservationState.UNKNOWN) {
            throw new IllegalStateException("知识入库 embedding 结果未知，需按 provider request id 人工确认");
        }
        if (reserved.state() == KnowledgeUsagePort.ReservationState.RECOVERABLE
                || reserved.state() == KnowledgeUsagePort.ReservationState.COMPLETED) {
            var provider =
                    com.xuejiai.aaf.common.util.JsonUtils.parseObject(
                            reserved.providerResult(), EmbeddingProviderResult.class);
            settleKnowledgeEmbedding(reserved, reserved.providerResult(), model, provider);
            cacheSettled(model, texts, provider.vectors());
            return List.copyOf(provider.vectors());
        }
        if (!knowledgeUsagePort.start(reserved)) {
            throw new IllegalStateException("知识入库 embedding 单元并发占用失败");
        }
        try {
            var embedded = embedBatchWithRetry(embeddingModel, texts);
            var vectors = embedded.vectors();
            var inputTokens = embedded.inputTokens();
            log.debug(
                    "[Embedding] 知识入库子单元完成: unitKey={}, count={}, inputTokens={}",
                    unitKey,
                    vectors.size(),
                    inputTokens);
            var provider = new EmbeddingProviderResult(vectors, inputTokens, vectors.size());
            var providerJson = com.xuejiai.aaf.common.util.JsonUtils.toJsonString(provider);
            knowledgeUsagePort.persistProviderResult(
                    reserved, providerJson, reserved.providerRequestId());
            settleKnowledgeEmbedding(reserved, providerJson, model, provider);
            cacheSettled(model, texts, vectors);
            return List.copyOf(vectors);
        } catch (RuntimeException failure) {
            // 调用已明确失败（未拿到 provider 结果），释放预留供重试；已落盘的结果不受影响
            knowledgeUsagePort.releaseForRetry(reserved, failure.getMessage());
            throw failure;
        }
    }

    private KnowledgeUsagePort.InvocationReservation reservation(
            BillingContext billing,
            String unitKey,
            KnowledgeUsagePort.StoredProviderResult stored) {
        return new KnowledgeUsagePort.InvocationReservation(
                billing,
                "embedding",
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

    private void settleKnowledgeEmbedding(
            KnowledgeUsagePort.InvocationReservation reservation,
            String providerJson,
            com.xuejiai.aaf.framework.intelligent.core.model.AiModel model,
            EmbeddingProviderResult provider) {
        var usage = new EmbeddingUsage(provider.inputTokens(), provider.count());
        var costYuan =
                Objects.requireNonNullElse(model.getInputPricePerK(), BigDecimal.ZERO)
                        .multiply(BigDecimal.valueOf(provider.inputTokens()))
                        .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
        knowledgeUsagePort.settle(
                new KnowledgeUsagePort.UsageCall(
                        reservation.usageKey(),
                        reservation.billing(),
                        reservation.stage(),
                        reservation.unitKey(),
                        reservation.invocationId(),
                        reservation.inputDigest(),
                        providerJson,
                        reservation.providerRequestId(),
                        model,
                        usage,
                        costYuan,
                        reservation.occurredAt()));
    }

    private void cacheSettled(AiModel model, List<String> texts, List<float[]> vectors) {
        for (var index = 0; index < texts.size(); index++) {
            cache.asMap().putIfAbsent(cacheKey(model, texts.get(index)), vectors.get(index));
        }
    }

    private record EmbeddedText(float[] vector, long inputTokens) {}

    private record EmbeddedBatch(List<float[]> vectors, long inputTokens) {}

    private record EmbeddingProviderResult(List<float[]> vectors, long inputTokens, int count) {
        private EmbeddingProviderResult {
            vectors = vectors == null ? List.of() : List.copyOf(vectors);
        }
    }

    private record EmbeddingUsage(long inputTokens, int count) implements AiUsage {
        @Override
        public Map<String, Object> standardUsage() {
            return Map.of("inputTokens", inputTokens, "outputTokens", 0, "count", count);
        }
    }
}
