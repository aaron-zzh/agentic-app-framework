package com.xuejiai.aaf.framework.engine.knowledge.chunker;

import java.util.Map;

/**
 * 文档分块结果
 */
public record DocumentChunk(
        String content,
        int index,
        Map<String, Object> metadata,
        int tokenCount
) {
}
