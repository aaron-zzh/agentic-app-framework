package com.xuejiai.aaf.module.ui.tracking;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 热力图数据 VO。 */
@Schema(description = "热力图数据")
public record HeatmapVO(
        @Schema(description = "页面路径") String page,
        @Schema(description = "热力点列表") List<HeatPoint> points) {
    @Schema(description = "热力点")
    public record HeatPoint(
            @Schema(description = "X 坐标") int x,
            @Schema(description = "Y 坐标") int y,
            @Schema(description = "点击次数") int count) {}
}
