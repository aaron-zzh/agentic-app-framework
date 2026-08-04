package com.xuejiai.aaf.module.ai.aigc.timeline.api;

public record AigcTimelineView(
        Long id, Long projectId, Long deliverableObjectId, String status, Integer version) {}
