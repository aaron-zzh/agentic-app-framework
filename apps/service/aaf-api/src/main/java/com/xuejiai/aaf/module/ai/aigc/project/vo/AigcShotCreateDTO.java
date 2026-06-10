package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotNull;

public record AigcShotCreateDTO(
        @NotNull Long storyboardId,
        @NotNull Integer shotNo,
        String name,
        String description,
        String dialogue,
        String properties) {}
