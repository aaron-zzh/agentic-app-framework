package com.xuejiai.aaf.framework.engine.knowledge.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Embedding 生成配置 */
@ConfigurationProperties(prefix = "aaf.knowledge.embedding")
public record EmbeddingProperties(String model, int dimensions, int batchSize, int maxRetries) {
    public EmbeddingProperties {
        // 全局唯一向量模型的 modelId（ai_model.model_id），入库、记忆、检索统一使用；切换会使已入库向量失效，需整体重建。
        if (model == null) model = "qwen:qwen3.7-text-embedding";
        if (dimensions == 0) dimensions = 1536;
        if (batchSize == 0) batchSize = 100;
        if (maxRetries == 0) maxRetries = 3;
    }
}
