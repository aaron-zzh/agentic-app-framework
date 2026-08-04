package com.xuejiai.aaf.module.ai.aigc.project.api;

public record AigcProjectObjectView(
        Long id, Long projectId, String objectType, String status, Long adoptedVersionId) {}
