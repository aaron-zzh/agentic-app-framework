package com.xuejiai.aaf.module.ai.aigc.timeline.vo;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AigcTimelineReplaceDTO(
        @NotNull @PositiveOrZero Integer expectedVersion,
        @NotNull List<@Valid AigcTimelineTrackDTO> tracks) {}
