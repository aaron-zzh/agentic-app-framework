package com.xuejiai.aaf.module.ai.aigc.project.api;

public record AigcProjectObjectCommand(
        Long projectId,
        Long parentObjectId,
        String blueprintTemplateKey,
        String objectType,
        String displayName,
        String contractRole,
        Integer orderNo,
        String schemaVersion,
        String payloadJson,
        Integer expectedProjectVersion) {}
