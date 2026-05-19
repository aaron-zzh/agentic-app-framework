package com.xuejiai.aaf.framework.engine.knowledge.pipeline;

import java.time.Instant;

/**
 * 管道执行进度
 */
public record PipelineProgress(
        Long documentId,
        PipelineStep currentStep,
        int totalSteps,
        int completedSteps,
        Instant startTime,
        String errorMessage
) {
}
