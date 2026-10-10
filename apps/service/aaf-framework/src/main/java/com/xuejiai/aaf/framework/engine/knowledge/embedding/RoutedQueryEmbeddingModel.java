package com.xuejiai.aaf.framework.engine.knowledge.embedding;

import java.util.function.Supplier;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 提供给 {@code PgVectorStore} 的 EmbeddingModel：不自行调用任何供应商，只回放调用方已经经 {@link EmbeddingService}（六层模型决策链
 * + 计费）算好的查询向量。
 *
 * <p>VectorStore 的检索接口只接收查询文本并在内部向量化，无法携带用户与路由上下文；为避免它绕过路由使用 Spring AI 自动装配的模型（与入库向量不在同一向量空间），
 * 检索方必须通过 {@link #withQueryVector} 在当前线程内绑定向量后同步检索。未绑定时直接失败，防止悄悄走回未路由的模型。
 */
@Primary
@Component
public class RoutedQueryEmbeddingModel implements EmbeddingModel {

    private static final ThreadLocal<float[]> QUERY_VECTOR = new ThreadLocal<>();

    private final EmbeddingProperties properties;

    public RoutedQueryEmbeddingModel(EmbeddingProperties properties) {
        this.properties = properties;
    }

    /** 在当前线程绑定预计算查询向量并同步执行检索，结束后还原。 */
    public static <T> T withQueryVector(float[] vector, Supplier<T> search) {
        var previous = QUERY_VECTOR.get();
        QUERY_VECTOR.set(vector);
        try {
            return search.get();
        } finally {
            if (previous == null) {
                QUERY_VECTOR.remove();
            } else {
                QUERY_VECTOR.set(previous);
            }
        }
    }

    @Override
    public float[] embed(String text) {
        var vector = QUERY_VECTOR.get();
        if (vector == null) {
            throw new IllegalStateException("查询向量未经 EmbeddingService 预计算，禁止直接调用 VectorStore 向量化");
        }
        return vector;
    }

    @Override
    public float[] embed(Document document) {
        throw new UnsupportedOperationException("文档向量化必须经 EmbeddingService（模型路由 + 计费）");
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        throw new UnsupportedOperationException("向量化必须经 EmbeddingService（模型路由 + 计费）");
    }

    @Override
    public int dimensions() {
        return properties.dimensions();
    }
}
