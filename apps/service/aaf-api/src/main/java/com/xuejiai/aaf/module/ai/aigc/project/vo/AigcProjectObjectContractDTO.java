package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AigcProjectObjectContractDTO(
        @NotBlank String contractRole,
        @NotNull @PositiveOrZero Integer expectedProjectVersion) {}
