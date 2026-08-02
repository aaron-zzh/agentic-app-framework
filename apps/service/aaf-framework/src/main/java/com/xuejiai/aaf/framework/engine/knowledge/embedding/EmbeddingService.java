package com.xuejiai.aaf.framework.engine.knowledge.embedding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.enums.pay.CreditTransactionCategoryEnum;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeUsagePort;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeUsagePort.BillingContext;
import com.xuejiai.aaf.framework.intelligent.core.AiUsage;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelManagementService;

import lombok.extern.slf4j.Slf4j;

/**
 * Embedding 生成服务，支持批量生成、缓存、重试。
 *
 * <p>M53：embedding 成本由触发用户承担（产品决策，2026-08-01）。门控与结算走 {@link AiCreditGuard}，按次固定计费（{@link
 * EmbeddingProperties#creditCostPerCall()}），不按 token 精算——embedding 单价远低于对话类调用，按次计费足以覆盖成本且避免为此单独接入
 * token 计量。
 *
 * <p>调用方（记忆抽取/去重/检索、知识库导入适配器等）必须传入承担成本的 {@code userId}；系统内部批量任务（如离线重建索引）若无自然归属用户，
 * 由调用方自行决定记账到系统账户或拒绝匿名调用，本服务不做静默兜底。
 */
@Slf4j
@Service
@EnableConfigurationProperties(EmbeddingProperties.class)
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingProperties properties;
    private final AiCreditGuard creditGuard;
    private final KnowledgeUsagePort knowledgeUsagePort;
    private final ModelManagementService modelManagementService;
    private final ConcurrentHashMap<String, float[]> cache = new ConcurrentHashMap<>();

    public EmbeddingService(
            EmbeddingModel embeddingModel,
            EmbeddingProperties properties,
            AiCreditGuard creditGuard,
            KnowledgeUsagePort knowledgeUsagePort,
            ModelManagementService modelManagementService) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
        this.creditGuard = creditGuard;
        this.knowledgeUsagePort = knowledgeUsagePort;
        this.modelManagementService = modelManagementService;
    }

    /** 单条文本生成 embedding，成本记账到 userId。 */
    public float[] embed(String text, Long userId) {
        Objects.requireNonNull(userId, "M53：embedding 成本需要归属用户，userId 不能为空");
        var key = sha256(text);
        var cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        creditGuard.precheck(
                userId,
                CreditTransactionCategoryEnum.EMBEDDING.getCode(),
                AiCreditGuard.INESTIMABLE_COST);
        var result = embedWithRetry(text);
        cache.putIfAbsent(key, result);
        creditGuard.settleFixed(
                userId,
                properties.creditCostPerCall(),
                CreditTransactionCategoryEnum.EMBEDDING.getCode());
        return result;
    }

    /** 批量生成 embedding，按 batchSize 分批调用，成本记账到 userId（按实际生成次数计费，缓存命中不重复计费）。 */
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

    /** 带重试的 embedding 调用 */
    private float[] embedWithRetry(String text) {
        for (int attempt = 1; attempt <= properties.maxRetries(); attempt++) {
            try {
                return embeddingModel.embed(text);
            } catch (Exception e) {
                log.warn("Embedding 生成失败，第 {} 次重试，原因：{}", attempt, e.getMessage());
                if (attempt == properties.maxRetries()) {
                    throw new RuntimeException(
                            "Embedding 生成失败，已重试 %d 次".formatted(properties.maxRetries()), e);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Embedding 重试被中断", ie);
                }
            }
        }
        throw new IllegalStateException("不可达");
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

    /** 知识入库批量向量化；调用前预留，provider 结果落盘后幂等结算。 */
    public List<float[]> embedKnowledgeBatch(
            List<String> texts, BillingContext billing, String unitKey) {
        Objects.requireNonNull(texts, "texts 不能为空");
        Objects.requireNonNull(billing, "billing 不能为空");
        if (texts.isEmpty()) {
            return List.of();
        }
        var model = modelManagementService.getModel(properties.model());
        var inputDigest =
                sha256(
                        properties.model()
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
            cacheSettled(texts, provider.vectors());
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
            cacheSettled(texts, provider.vectors());
            return List.copyOf(provider.vectors());
        }
        if (!knowledgeUsagePort.start(reserved)) {
            throw new IllegalStateException("知识入库 embedding 单元并发占用失败");
        }
        try {
            var vectors = texts.stream().map(this::embedWithRetry).toList();
            var inputTokens =
                    texts.stream().mapToLong(text -> Math.max(1, text.length() / 4)).sum();
            var provider = new EmbeddingProviderResult(vectors, inputTokens, vectors.size());
            var providerJson = com.xuejiai.aaf.common.util.JsonUtils.toJsonString(provider);
            knowledgeUsagePort.persistProviderResult(
                    reserved, providerJson, reserved.providerRequestId());
            settleKnowledgeEmbedding(reserved, providerJson, model, provider);
            cacheSettled(texts, vectors);
            return List.copyOf(vectors);
        } catch (RuntimeException failure) {
            knowledgeUsagePort.markUnknown(reserved, failure.getMessage());
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

    private void cacheSettled(List<String> texts, List<float[]> vectors) {
        for (var index = 0; index < texts.size(); index++) {
            cache.putIfAbsent(sha256(texts.get(index)), vectors.get(index));
        }
    }

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
