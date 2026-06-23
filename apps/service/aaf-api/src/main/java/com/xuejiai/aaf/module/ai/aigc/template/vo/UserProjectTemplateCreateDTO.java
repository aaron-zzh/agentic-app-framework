package com.xuejiai.aaf.module.ai.aigc.template.vo;

import jakarta.validation.constraints.NotBlank;

public record UserProjectTemplateCreateDTO(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        String coverUrl,
        @NotBlank String category,
        @NotBlank String projectType,
        String templateConfig,
        Boolean isOfficial,
        Integer sortOrder) {}
