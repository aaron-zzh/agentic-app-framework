package com.xuejiai.aaf.module.ai.aigc.project.api;

public record AigcObjectVersionAdoptCommand(
        Long projectId,
        Long objectId,
        Long objectVersionId,
        Long expectedAdoptedVersionId,
        Integer expectedProjectVersion,
        boolean confirmedReplacement,
        String reason,
        String idempotencyKey) {}
