package com.xuejiai.aaf.module.ai.aigc.task.api;

public record AigcTaskSubmitCommand(
        Long executionRunId,
        Long projectId,
        Long projectObjectId,
        String taskType,
        String modelId,
        String prompt,
        String parametersJson,
        String idempotencyKey) {}
