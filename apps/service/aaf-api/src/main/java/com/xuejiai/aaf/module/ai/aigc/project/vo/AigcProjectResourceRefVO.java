package com.xuejiai.aaf.module.ai.aigc.project.vo;

public record AigcProjectResourceRefVO(
        Long id,
        String resourceType,
        String resourceId,
        String resourceVersion,
        String role,
        Integer sortOrder) {}
