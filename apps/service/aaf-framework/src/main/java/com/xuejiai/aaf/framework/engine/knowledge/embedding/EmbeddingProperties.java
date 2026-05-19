package com.xuejiai.aaf.framework.engine.knowledge.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Embedding 生成配置
 */
@ConfigurationProperties(prefix = "aaf.knowledge.embedding")
public record EmbeddingProperties(
        String model,
        int dimensions,
        int batchSize,
        int maxRetries
) {
    public EmbeddingProperties {
        if (model == null) model = "text-embedding-3-small";
        if (dimensions == 0) dimensions = 1536;
        if (batchSize == 0) batchSize = 100;
        if (maxRetries == 0) maxRetries = 3;
    }
}
