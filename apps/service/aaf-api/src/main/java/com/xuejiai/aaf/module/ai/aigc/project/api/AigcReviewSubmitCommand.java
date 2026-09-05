package com.xuejiai.aaf.module.ai.aigc.project.api;

public record AigcReviewSubmitCommand(
        Long projectId,
        Long setObjectId,
        Long manifestObjectVersionId,
        Integer expectedProjectVersion,
        String idempotencyKey) {}
