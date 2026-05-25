package com.xuejiai.aaf.framework.engine.evolution;

import java.util.List;

/**
 * 自进化引擎——驱动系统自我优化和能力生长。
 *
 * <p>职责：技能自动生成、Prompt 优化、策略进化。
 * 与 Learning 反哺通道配合：Learning 产出知识 → Evolution 将知识转化为能力提升。
 * v0.3+ 实现。
 */
public interface EvolutionEngine {

    /** 提交进化提案（需人工审核）。 */
    String proposeEvolution(EvolutionProposal proposal);

    /** 查询待审核提案。 */
    List<EvolutionProposal> pendingProposals();

    /** 进化提案 */
    record EvolutionProposal(Type type, String title, String description, String content) {}

    /** 进化类型 */
    enum Type {
        /** 新技能生成 */
        SKILL_GENERATION,
        /** Prompt 模板优化 */
        PROMPT_OPTIMIZATION,
        /** 策略规则更新 */
        STRATEGY_UPDATE,
        /** 价值观更新建议 */
        VALUE_UPDATE
    }
}
