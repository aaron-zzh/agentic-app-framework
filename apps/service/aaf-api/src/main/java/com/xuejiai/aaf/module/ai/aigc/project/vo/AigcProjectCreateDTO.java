package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotBlank;

public record AigcProjectCreateDTO(
        @NotBlank String name, String coverUrl, String description, String type, String prompt) {}
