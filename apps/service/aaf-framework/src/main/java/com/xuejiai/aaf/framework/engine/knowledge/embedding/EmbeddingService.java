package com.xuejiai.aaf.framework.engine.knowledge.embedding;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Embedding 生成服务，支持批量生成、缓存、重试。
 *
 * <p>M53（未闭环，需计费策略决策）：本服务**不做积分/配额门控也不计量**——接口连 ownerId 都没有， 无法归属成本。对比同层的 {@code
 * ResilientChatService}：它在 call/stream 两条路径都做了 {@code creditGuard.precheck} 与用量事件发布。
 *
 * <p>要闭环需要先定两件事，再改接口与全部调用方（记忆抽取/去重/检索、知识库导入适配器等）：
 *
 * <ol>
 *   <li>系统触发的 embedding（会话后异步记忆抽取、知识库批量导入）由谁承担成本
 *   <li>单次 embedding 的计费口径（按次还是按 token）
 * </ol>
 *
 * <p>在此之前：调用方不得把 embedding 当作免费操作；新增调用路径必须同步评估成本，不要因为这里没有 门控就默认放行。
 */
@Slf4j
@Service
@EnableConfigurationProperties(EmbeddingProperties.class)
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingProperties properties;
    private final ConcurrentHashMap<String, float[]> cache = new ConcurrentHashMap<>();

    public EmbeddingService(EmbeddingModel embeddingModel, EmbeddingProperties properties) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    /** 单条文本生成 embedding */
    public float[] embed(String text) {
        var key = sha256(text);
        return cache.computeIfAbsent(key, k -> embedWithRetry(text));
    }

    /** 批量生成 embedding，按 batchSize 分批调用 */
    public List<float[]> embedBatch(List<String> texts, int batchSize) {
        var results = new ArrayList<float[]>(texts.size());
        for (int i = 0; i < texts.size(); i += batchSize) {
            var batch = texts.subList(i, Math.min(i + batchSize, texts.size()));
            for (var text : batch) {
                results.add(embed(text));
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
