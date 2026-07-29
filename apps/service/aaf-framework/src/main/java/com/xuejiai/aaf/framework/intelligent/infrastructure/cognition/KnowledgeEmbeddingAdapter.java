package com.xuejiai.aaf.framework.intelligent.infrastructure.cognition;

import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingProperties;
import com.xuejiai.aaf.framework.intelligent.ai.embedding.EmbeddingService;

/** 将现有知识库 Embedding 生产服务接入智能层的唯一适配器。 */
public final class KnowledgeEmbeddingAdapter implements EmbeddingService {

    private final com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingService delegate;
    private final EmbeddingProperties properties;

    public KnowledgeEmbeddingAdapter(
            com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingService delegate,
            EmbeddingProperties properties) {
        this.delegate = Objects.requireNonNull(delegate, "delegate 不能为空");
        this.properties = Objects.requireNonNull(properties, "properties 不能为空");
    }

    @Override
    public float[] embed(String text, String modelId) {
        requireConfiguredModel(modelId);
        return delegate.embed(text);
    }

    @Override
    public List<float[]> embed(List<String> texts, String modelId) {
        requireConfiguredModel(modelId);
        return delegate.embedBatch(texts, properties.batchSize());
    }

    private void requireConfiguredModel(String modelId) {
        if (!properties.model().equals(modelId)) {
            throw new IllegalArgumentException("Cognition embedding modelId 未接线: " + modelId);
        }
    }
}
