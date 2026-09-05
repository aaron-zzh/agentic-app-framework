package com.xuejiai.aaf.module.ai.aigc.project.api;

public record AigcProjectObjectView(
        Long id,
        Long projectId,
        String stableKey,
        String blueprintTemplateKey,
        Integer instanceNo,
        String objectType,
        String displayName,
        String contractRole,
        String defaultActionKey,
        Long parentObjectId,
        String status,
        Long adoptedVersionId) {}
