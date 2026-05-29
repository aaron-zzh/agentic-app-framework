package com.xuejiai.aaf.module.stats.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 漏斗分析结果。
 */
@Schema(description = "漏斗分析")
public record FunnelVO(
        @Schema(description = "漏斗步骤列表") List<Step> steps
) {
    @Schema(description = "漏斗步骤")
    public record Step(
            @Schema(description = "阶段名称") String stage,
            @Schema(description = "人数") long count,
            @Schema(description = "转化率（相对上一步）") Double conversionRate
    ) {}
}
