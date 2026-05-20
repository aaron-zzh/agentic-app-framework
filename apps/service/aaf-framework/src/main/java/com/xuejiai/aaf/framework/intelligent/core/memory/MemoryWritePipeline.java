package com.xuejiai.aaf.framework.intelligent.core.memory;

/**
 * 写管道接口——对话结束后将交互内容持久化到记忆系统。
 *
 * <p>固定流程，不可编排：提取 → 去重 → 写入 → 遗忘。
 * 写入逻辑是数据一致性保障，步骤不可跳过或重排，防止记忆污染、重复、丢失。
 *
 * <p>唯一可配置点：提取策略（提取什么），通过 {@link MemoryStrategy} 控制提取粒度，
 * 但处理步骤顺序固定不变。
 *
 * @see RetrievalPipeline 读管道（可编排）
 */
public interface MemoryWritePipeline {

    /**
     * 执行写管道，将本轮对话内容持久化到记忆系统。
     *
     * @param input 写入输入（对话内容、userId、sessionId）
     */
    void execute(WriteInput input);

    /**
     * 写管道输入。
     *
     * @param userMessage   用户消息
     * @param assistantReply 助理回复
     * @param userId        用户 ID
     * @param sessionId     会话 ID
     * @param strategy      提取策略（控制提取粒度，不影响步骤顺序）
     */
    record WriteInput(
        String userMessage,
        String assistantReply,
        Long userId,
        String sessionId,
        MemoryStrategy strategy
    ) {}
}
