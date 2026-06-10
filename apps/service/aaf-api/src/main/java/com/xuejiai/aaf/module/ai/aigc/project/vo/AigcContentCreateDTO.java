package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AigcContentCreateDTO(
        @NotNull Long projectId,
        @NotBlank String type,
        String title,
        Long docId,
        String platform) {}
