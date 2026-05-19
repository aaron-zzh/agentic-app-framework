package com.xuejiai.aaf.framework.intelligent.core.memory;

/**
 * 记忆管道策略——决定从哪些源拉取上下文。
 * Assistant 配置此策略，MemoryPipelineFactory 据此选择对应 Pipeline 实现。
 */
public enum MemoryStrategy {
    /** 仅查个人记忆（个人助理场景） */
    MEMORY_ONLY,
    /** 仅查知识库（客服/问答场景） */
    KNOWLEDGE_ONLY,
    /** 记忆 + 知识库混合（默认） */
    HYBRID,
    /** 程序化记忆优先（代码助理/经验场景） */
    PROCEDURAL_FIRST,
    /** 全源检索（记忆 + 知识库 + 程序化 + 图谱） */
    FULL
}
