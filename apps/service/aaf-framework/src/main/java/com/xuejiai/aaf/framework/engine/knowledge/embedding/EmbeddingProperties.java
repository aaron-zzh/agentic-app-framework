package com.xuejiai.aaf.framework.engine.knowledge.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Embedding 生成配置 */
@ConfigurationProperties(prefix = "aaf.knowledge.embedding")
public record EmbeddingProperties(
        String model, int dimensions, int batchSize, int maxRetries, long creditCostPerCall) {
    public EmbeddingProperties {
        if (model == null) model = "text-embedding-3-small";
        if (dimensions == 0) dimensions = 1536;
        if (batchSize == 0) batchSize = 100;
        if (maxRetries == 0) maxRetries = 3;
        // M53：embedding 成本由触发用户承担，按次固定计费（不按 token 精算）。
        // 默认 1 积分/次，运营可通过 aaf.knowledge.embedding.credit-cost-per-call 调整。
        if (creditCostPerCall == 0) creditCostPerCall = 1;
    }
}
