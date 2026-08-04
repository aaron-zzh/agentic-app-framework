package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AigcProjectVersionDTO(@NotNull @PositiveOrZero Integer expectedVersion) {}
