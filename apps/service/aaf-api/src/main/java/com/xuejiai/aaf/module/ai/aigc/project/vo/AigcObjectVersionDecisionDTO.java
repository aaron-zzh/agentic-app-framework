package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AigcObjectVersionDecisionDTO(
        @NotNull @PositiveOrZero Integer expectedProjectVersion, String reason) {}
