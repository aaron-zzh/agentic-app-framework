package com.xuejiai.aaf.framework.engine.knowledge.embedding;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.enums.pay.CreditTransactionCategoryEnum;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;

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
    private final ConcurrentHashMap<String, float[]> cache = new ConcurrentHashMap<>();

    public EmbeddingService(
            EmbeddingModel embeddingModel, EmbeddingProperties properties, AiCreditGuard creditGuard) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
        this.creditGuard = creditGuard;
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
                userId, CreditTransactionCategoryEnum.EMBEDDING.getCode(), AiCreditGuard.INESTIMABLE_COST);
        var result = embedWithRetry(text);
        cache.putIfAbsent(key, result);
        creditGuard.settleFixed(
                userId, properties.creditCostPerCall(), CreditTransactionCategoryEnum.EMBEDDING.getCode());
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
}
