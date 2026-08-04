package com.xuejiai.aaf.module.ai.aigc.project.api;

public record AigcProjectObjectCommand(
        Long projectId,
        Long parentObjectId,
        String stableKey,
        String objectType,
        Integer orderNo,
        String schemaVersion,
        String payloadJson,
        Integer expectedProjectVersion) {}
