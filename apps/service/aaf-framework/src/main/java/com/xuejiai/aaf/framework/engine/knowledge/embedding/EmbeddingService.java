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

/** Embedding 生成服务，支持批量生成、缓存、重试 */
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
