package com.xuejiai.aaf.module.ai.aigc.template.vo;

public record UserProjectTemplateUpdateDTO(
        String name,
        String description,
        String coverUrl,
        String category,
        String projectType,
        String templateConfig,
        Boolean isOfficial,
        Integer sortOrder) {}
