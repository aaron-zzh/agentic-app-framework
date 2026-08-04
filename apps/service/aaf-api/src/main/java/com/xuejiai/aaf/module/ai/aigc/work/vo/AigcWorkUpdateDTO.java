package com.xuejiai.aaf.module.ai.aigc.work.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AigcWorkUpdateDTO(
        @Size(max = 300) String title,
        Long coverMediaVersionId,
        @Size(max = 32) String visibility,
        @NotNull @PositiveOrZero Integer expectedVersion) {}
