package com.xuejiai.aaf.module.ai.aigc.project.api;

public record AigcObjectVersionRejectCommand(
        Long projectId,
        Long objectId,
        Long objectVersionId,
        Integer expectedProjectVersion,
        String reason,
        String idempotencyKey) {}
