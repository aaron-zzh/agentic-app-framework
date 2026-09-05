package com.xuejiai.aaf.module.ai.aigc.work.api;

public record AigcWorkView(
        Long id,
        Long projectId,
        Long deliverableSetObjectId,
        Long manifestObjectVersionId,
        String status,
        Integer version) {}
