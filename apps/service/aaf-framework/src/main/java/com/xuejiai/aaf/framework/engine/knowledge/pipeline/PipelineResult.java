package com.xuejiai.aaf.framework.engine.knowledge.pipeline;

/**
 * 管道执行结果
 */
public record PipelineResult(
        boolean success,
        Long documentId,
        int chunkCount,
        int embeddingCount,
        long durationMs,
        String errorMessage
) {
}
