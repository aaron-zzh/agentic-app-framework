package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotNull;

public record AigcTimelineCreateDTO(
        @NotNull Long projectId, Long storyboardId, String title, Short fps, String resolution) {}
