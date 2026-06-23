/**
 * 注意力资源预算分配实现。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.agent.runtime;

import org.springframework.stereotype.Service;

/** Token 预算分配：按比例分配剩余 Token 给各信息源。 策略：输出预留 → 记忆 30% → 知识 40% → 历史 20% → 工具 10% */
@Service
public class AttentionBudgetImpl implements AttentionBudget {

    private static final double MEMORY_RATIO = 0.30;
    private static final double KNOWLEDGE_RATIO = 0.40;
    private static final double HISTORY_RATIO = 0.20;
    private static final double TOOL_RATIO = 0.10;
    private static final int OUTPUT_RESERVE = 2048;

    @Override
    public Allocation allocate(int totalTokens, int systemPromptTokens, int userInputTokens) {
        int available = totalTokens - systemPromptTokens - userInputTokens - OUTPUT_RESERVE;
        available = Math.max(0, available);

        return new Allocation(
                (int) (available * MEMORY_RATIO),
                (int) (available * KNOWLEDGE_RATIO),
                (int) (available * TOOL_RATIO),
                (int) (available * HISTORY_RATIO),
                OUTPUT_RESERVE);
    }
}
