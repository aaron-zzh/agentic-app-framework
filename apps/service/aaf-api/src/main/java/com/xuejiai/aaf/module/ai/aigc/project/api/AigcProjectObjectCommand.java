package com.xuejiai.aaf.module.ai.aigc.project.api;

public record AigcProjectObjectCommand(
        Long projectId,
        Long parentObjectId,
        String blueprintTemplateKey,
        String objectType,
        String displayName,
        String contractRole,
        Integer orderNo,
        String semanticRole,
        String outputContract,
        String schemaVersion,
        String payloadJson,
        Integer expectedProjectVersion,
        Long expectedGraphRevision) {}
