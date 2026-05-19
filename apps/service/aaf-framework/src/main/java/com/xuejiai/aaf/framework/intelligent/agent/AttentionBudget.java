/**
 * 注意力资源管理接口——Token 预算的有限分配。
 *
 * <p>对齐认知心理学：注意力是有限资源，需要在多个信息源间分配。
 * 在 LLM 场景中，注意力 = 上下文窗口 Token 的分配策略。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.agent;

/**
 * 注意力资源管理：决定 LLM prompt 中各部分的 Token 预算。
 * 混合检索时实现——检索结果需要在有限窗口内合理分配。
 */
public interface AttentionBudget {

    /**
     * 计算各部分的 Token 预算分配。
     *
     * @param totalTokens 总可用 Token 数（模型上下文窗口）
     * @param systemPromptTokens 系统提示词已占用的 Token
     * @param userInputTokens 用户输入已占用的 Token
     * @return 各部分的预算分配
     */
    Allocation allocate(int totalTokens, int systemPromptTokens, int userInputTokens);

    /** Token 预算分配结果 */
    record Allocation(
        /** 记忆上下文可用 Token */
        int memoryBudget,
        /** 知识库检索结果可用 Token */
        int knowledgeBudget,
        /** 工具调用结果可用 Token */
        int toolResultBudget,
        /** 对话历史可用 Token */
        int historyBudget,
        /** 预留给模型输出的 Token */
        int outputReserve
    ) {}
}
