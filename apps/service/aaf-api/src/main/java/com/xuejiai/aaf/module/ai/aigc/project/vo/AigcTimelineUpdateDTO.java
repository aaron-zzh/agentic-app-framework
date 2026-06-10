package com.xuejiai.aaf.module.ai.aigc.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** 更新时间轴请求 DTO。 */
public record AigcTimelineUpdateDTO(
        @Size(max = 200) @Schema(description = "标题") String title,
        @Schema(description = "状态") String status,
        @Schema(description = "总时长（毫秒）") Long durationMs,
        @Schema(description = "帧率") Integer fps,
        @Schema(description = "分辨率") String resolution) {}
