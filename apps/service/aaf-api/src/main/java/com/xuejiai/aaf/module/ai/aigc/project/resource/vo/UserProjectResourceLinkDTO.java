package com.xuejiai.aaf.module.ai.aigc.project.resource.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserProjectResourceLinkDTO(
        @NotBlank String resourceType, @NotNull Long resourceId, String role, Integer sortOrder) {}
