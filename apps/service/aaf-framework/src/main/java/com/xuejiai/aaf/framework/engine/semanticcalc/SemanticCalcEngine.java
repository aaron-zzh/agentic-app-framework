package com.xuejiai.aaf.framework.engine.semanticcalc;

import java.util.List;

/**
 * 语义计算引擎——语义相似度、语义聚类、语义推理。
 *
 * <p>职责：为上层提供语义级计算能力（非简单向量检索）。 v0.2+ 实现。
 */
public interface SemanticCalcEngine {

    /** 计算两段文本的语义相似度（0.0-1.0）。 */
    double similarity(String textA, String textB);

    /** 语义聚类。 */
    List<List<String>> cluster(List<String> texts, int k);

    /** 语义蕴含判断。 */
    EntailmentResult entailment(String premise, String hypothesis);

    /** 蕴含结果 */
    record EntailmentResult(Relation relation, double confidence) {}

    /** 蕴含关系 */
    enum Relation {
        ENTAILMENT,
        CONTRADICTION,
        NEUTRAL
    }
}
