package com.xuejiai.aaf.module.ai.aigc.timeline.api;

public record AigcTimelineDeleteCommand(
        Long projectId,
        Long timelineId,
        Integer expectedProjectVersion,
        Integer expectedVersion) {}
