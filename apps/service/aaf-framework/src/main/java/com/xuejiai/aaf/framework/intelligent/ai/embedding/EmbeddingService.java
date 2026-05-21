package com.xuejiai.aaf.framework.intelligent.ai.embedding;

import java.util.List;

/** 向量化服务，支持按模型动态切换。 */
public interface EmbeddingService {

    /** 单文本向量化 */
    float[] embed(String text, String modelId);

    /** 批量向量化 */
    List<float[]> embed(List<String> texts, String modelId);
}
