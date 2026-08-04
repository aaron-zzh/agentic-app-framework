package com.xuejiai.aaf.module.ai.aigc.timeline.api;

public record AigcStoryboardExportView(
        Long id,
        Long projectId,
        Integer sourceRevisionNo,
        Long outputMediaVersionId,
        String format) {}
