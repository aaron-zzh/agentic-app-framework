package com.xuejiai.aaf.module.stats.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 留存分析结果。
 */
@Schema(description = "留存分析")
public record RetentionVO(
        @Schema(description = "留存数据点") List<RetentionPoint> points
) {
    @Schema(description = "留存数据点")
    public record RetentionPoint(
            @Schema(description = "留存天数（1/7/30）") int day,
            @Schema(description = "留存率") double rate,
            @Schema(description = "留存人数") long count,
            @Schema(description = "基准人数") long base
    ) {}
}
