package com.xuejiai.aaf.framework.intelligent.ai.embedding;

import java.util.List;

import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.SubjectKind;

/** 向量化服务，支持按模型动态切换。 */
public interface EmbeddingService {

    /**
     * 单文本向量化。
     *
     * <p>M53：embedding 成本由触发主体承担。{@code subjectKind=VISITOR}（匿名访客）无法归属计费账户，实现方必须拒绝
     * （产品决策，2026-08-01：不做系统账户兜底）。
     *
     * @param subjectKind 调用主体类型
     * @param subjectId 调用主体标识——{@code subjectKind=USER} 时为 userId 字符串形式
     */
    float[] embed(String text, String modelId, SubjectKind subjectKind, String subjectId);

    /** 批量向量化，语义同 {@link #embed(String, String, SubjectKind, String)}。 */
    List<float[]> embed(
            List<String> texts, String modelId, SubjectKind subjectKind, String subjectId);
}
