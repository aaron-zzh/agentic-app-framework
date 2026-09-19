package com.xuejiai.aaf.module.ai.aigc.task.api;

public record AigcTaskProgressView(
        Long id,
        Long executionRunId,
        String taskType,
        String status,
        Long outputMediaVersionId,
        Long creditTxId,
        String failureReason) {}
