package com.xuejiai.aaf.framework.intelligent.infrastructure.cognition;

import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingProperties;
import com.xuejiai.aaf.framework.intelligent.ai.embedding.EmbeddingService;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.SubjectKind;

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
    public float[] embed(String text, String modelId, SubjectKind subjectKind, String subjectId) {
        requireConfiguredModel(modelId);
        return delegate.embed(text, requireBillableUserId(subjectKind, subjectId));
    }

    @Override
    public List<float[]> embed(
            List<String> texts, String modelId, SubjectKind subjectKind, String subjectId) {
        requireConfiguredModel(modelId);
        return delegate.embedBatch(
                texts, properties.batchSize(), requireBillableUserId(subjectKind, subjectId));
    }

    private void requireConfiguredModel(String modelId) {
        if (!properties.model().equals(modelId)) {
            throw new IllegalArgumentException("Cognition embedding modelId 未接线: " + modelId);
        }
    }

    /**
     * M53：VISITOR（匿名访客）无法归属计费账户，直接拒绝（产品决策，2026-08-01：不做系统账户兜底）。 USER 场景把字符串 subjectId 转换为计费用的
     * userId。
     */
    private Long requireBillableUserId(SubjectKind subjectKind, String subjectId) {
        if (subjectKind != SubjectKind.USER) {
            throw new IllegalStateException(
                    "M53：匿名访客（%s）不支持生成 embedding，无法归属计费账户".formatted(subjectKind));
        }
        try {
            return Long.parseLong(subjectId);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("M53：USER subjectId 非法，无法归属计费账户: " + subjectId, e);
        }
    }
}
