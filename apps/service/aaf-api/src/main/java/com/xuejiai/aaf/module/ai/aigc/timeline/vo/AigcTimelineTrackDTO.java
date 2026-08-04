package com.xuejiai.aaf.module.ai.aigc.timeline.vo;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AigcTimelineTrackDTO(
        @NotBlank @Size(max = 20) String trackType,
        @Size(max = 100) String name,
        @NotNull @PositiveOrZero Integer orderNo,
        Boolean muted,
        Boolean locked,
        @NotNull List<@Valid AigcTimelineClipDTO> clips) {}
