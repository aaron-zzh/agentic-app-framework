package com.xuejiai.aaf.module.ai.aigc.work.api;

public record AigcWorkView(
        Long id,
        Long projectId,
        Long deliverableObjectId,
        Long adoptedObjectVersionId,
        String status,
        Integer version) {}
