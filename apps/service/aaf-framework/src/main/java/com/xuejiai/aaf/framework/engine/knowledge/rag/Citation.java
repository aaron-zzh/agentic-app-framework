package com.xuejiai.aaf.framework.engine.knowledge.rag;

/**
 * 引用溯源信息
 */
public record Citation(
        int index,
        String content,
        String source,
        String documentId
) {}
