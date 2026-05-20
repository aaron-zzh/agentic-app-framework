package com.xuejiai.aaf.framework.intelligent.core.memory;

/**
 * 读管道接口——按 MemoryStrategy 从多源检索并组装上下文。
 *
 * <p>可编排：不同场景（个人助理/客服/代码助理）配置不同的检索策略，
 * 数据源组合、融合权重、Token 截断优先级均可灵活配置。
 *
 * <p>管道步骤：查询理解 → 路由决策 → 并行检索 → RRF 融合 → LLM 重排 → MemoryContext
 *
 * <p>重命名自 {@code MemoryPipeline}，原名混淆了读/写两种管道语义。
 * 写管道见 {@link MemoryWritePipeline}（固定流程，不可编排）。
 *
 * @see MemoryStrategy
 * @see MemoryWritePipeline
 */
public interface RetrievalPipeline {

    /**
     * 执行检索管道，返回可注入 Prompt 的上下文块。
     *
     * @param input 检索输入（query、userId、sessionId、knowledgeBaseId）
     * @return 组装好的记忆上下文
     */
    MemoryContext execute(PipelineInput input);
}
