package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.util.Map;

public record AigcProjectObjectVO(
        Long id,
        Integer version,
        Long projectId,
        String objectType,
        String stableKey,
        String blueprintTemplateKey,
        Integer instanceNo,
        String contractRole,
        Long parentId,
        Integer sortOrder,
        String title,
        String status,
        String source,
        String schemaVersion,
        String entityResource,
        Long entityId,
        Long adoptedVersionId,
        String summary,
        Map<String, Object> payload) {}
