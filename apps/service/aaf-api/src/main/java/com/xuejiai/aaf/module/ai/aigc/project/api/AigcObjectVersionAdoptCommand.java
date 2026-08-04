package com.xuejiai.aaf.module.ai.aigc.project.api;

public record AigcObjectVersionAdoptCommand(
        Long projectId,
        Long objectId,
        Long objectVersionId,
        Integer expectedProjectVersion,
        String reason) {}
