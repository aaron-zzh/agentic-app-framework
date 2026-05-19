package com.xuejiai.aaf.framework.engine.knowledge.rag;

/**
 * 混合检索配置
 */
public record HybridSearchConfig(
        double vectorWeight,
        double bm25Weight,
        double graphWeight,
        int topK
) {
    public HybridSearchConfig() {
        this(0.5, 0.3, 0.2, 10);
    }
}
