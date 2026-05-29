package com.xuejiai.aaf.module.stats.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 趋势数据点（供 ECharts 渲染）。
 */
@Schema(description = "趋势数据点")
public record TrendPointVO(
        @Schema(description = "时间标签") String time,
        @Schema(description = "指标值") Long value
) {}
