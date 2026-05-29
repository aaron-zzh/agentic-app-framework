package com.xuejiai.aaf.module.stats.vo;

import java.time.LocalDate;

import com.xuejiai.aaf.common.enums.stats.StatPeriodEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 趋势查询请求。
 */
@Schema(description = "趋势查询参数")
public record TrendQueryDTO(
        @NotNull @Schema(description = "指标名：dau/mau/messages/tokens/revenue") String metric,
        @NotNull @Schema(description = "时间粒度") StatPeriodEnum period,
        @NotNull @Schema(description = "开始日期") LocalDate startDate,
        @NotNull @Schema(description = "结束日期") LocalDate endDate
) {}
