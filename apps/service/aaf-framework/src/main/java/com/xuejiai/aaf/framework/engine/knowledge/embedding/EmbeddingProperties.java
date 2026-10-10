package com.xuejiai.aaf.framework.engine.knowledge.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Embedding 生成配置 */
@ConfigurationProperties(prefix = "aaf.knowledge.embedding")
public record EmbeddingProperties(
        String model, int dimensions, int batchSize, int maxRetries, long creditCostPerCall) {
    public EmbeddingProperties {
        // 全局唯一向量模型的 modelId（ai_model.model_id），入库、记忆、检索统一使用；切换会使已入库向量失效，需整体重建。
        if (model == null) model = "qwen:text-embedding-v4";
        if (dimensions == 0) dimensions = 1536;
        if (batchSize == 0) batchSize = 100;
        if (maxRetries == 0) maxRetries = 3;
        // M53：embedding 成本由触发用户承担，按次固定计费（不按 token 精算）。
        // 默认 1 积分/次，运营可通过 aaf.knowledge.embedding.credit-cost-per-call 调整。
        if (creditCostPerCall == 0) creditCostPerCall = 1;
    }
}
