package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.util.Map;

public record AigcProjectObjectVO(
        Long id,
        Integer version,
        Long projectId,
        String objectType,
        String objectKey,
        String blueprintNodeKey,
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
