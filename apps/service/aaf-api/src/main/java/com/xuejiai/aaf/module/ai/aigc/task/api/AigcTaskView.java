package com.xuejiai.aaf.module.ai.aigc.task.api;

public record AigcTaskView(
        Long id,
        Long executionRunId,
        String taskType,
        String status,
        Long outputMediaVersionId,
        String failureCode) {}
