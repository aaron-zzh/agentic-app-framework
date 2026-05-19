package com.xuejiai.aaf.framework.intelligent.core.memory;

/**
 * 记忆管道接口——上下文组装流水线。
 * 按策略从多个存储拉取内容，按 Token 预算裁剪，格式化后组装为 Prompt 可注入的上下文块。
 *
 * <p>管道步骤：查询理解 → 路由决策 → 并行检索（混合检索）→ RRF 融合 → LLM 重排 → MemoryContext
 */
public interface MemoryPipeline {

    /**
     * 执行记忆管道，返回可注入 Prompt 的上下文块。
     */
    MemoryContext execute(PipelineInput input);
}
