package com.xuejiai.aaf.framework.intelligent.core.memory;

/**
 * 记忆管道输入。
 */
public record PipelineInput(
    String query,
    Long userId,
    String conversationId,
    Long knowledgeBaseId,
    int maxTokens
) {
    /** 4 参数构造器（不指定 maxTokens，使用默认值 2000） */
    public PipelineInput(String query, Long userId, String conversationId, Long knowledgeBaseId) {
        this(query, userId, conversationId, knowledgeBaseId, 2000);
    }

    /** 3 参数构造器（无知识库） */
    public PipelineInput(String query, Long userId, String conversationId) {
        this(query, userId, conversationId, null, 2000);
    }
}
