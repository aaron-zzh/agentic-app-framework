package com.xuejiai.aaf.module.ai.aigc.project.api;

public record AigcReviewDecisionCommand(
        Long projectId,
        Long reviewObjectId,
        Long expectedManifestObjectVersionId,
        Integer expectedProjectVersion,
        Integer expectedReviewVersion,
        String comment,
        String idempotencyKey) {}
