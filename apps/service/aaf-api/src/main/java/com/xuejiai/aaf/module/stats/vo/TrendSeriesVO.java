package com.xuejiai.aaf.module.stats.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 趋势图表数据（ECharts series 格式）。
 *
 * <p>前端直接映射：categories → xAxis.data，series[].data → series[].data
 */
@Schema(description = "趋势图表数据")
public record TrendSeriesVO(
        @Schema(description = "X轴时间标签列表") List<String> categories,
        @Schema(description = "数据系列") List<Series> series) {
    @Schema(description = "单条数据系列")
    public record Series(
            @Schema(description = "系列名称") String name,
            @Schema(description = "数据值列表") List<Long> data) {}
}
