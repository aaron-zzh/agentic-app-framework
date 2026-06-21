package com.xuejiai.aaf.module.stats.vo;

import java.time.LocalDate;

import com.xuejiai.aaf.common.enums.stats.StatPeriodEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 趋势查询请求。 */
@Schema(description = "趋势查询参数")
public record TrendQueryDTO(
        @NotNull @Schema(description = "指标名：dau/mau/messages/tokens/revenue/aigc_task/credit_cost")
                String metric,
        @NotNull @Schema(description = "时间粒度") StatPeriodEnum period,
        @Schema(description = "开始日期（不传时按粒度自动推算）") LocalDate startDate,
        @Schema(description = "结束日期（不传时为今天）") LocalDate endDate,
        @Schema(description = "用户 ID 过滤，null=全局（管理员）") Long userId) {

    /** 返回有效的开始日期：未传时按粒度推算默认范围。 */
    public LocalDate effectiveStartDate() {
        if (startDate != null) return startDate;
        return switch (period) {
            case HOUR -> java.time.LocalDate.now();
            case DAY -> java.time.LocalDate.now().minusDays(30);
            default -> java.time.LocalDate.now().minusDays(7);
        };
    }

    /** 返回有效的结束日期：未传时为今天。 */
    public LocalDate effectiveEndDate() {
        return endDate != null ? endDate : java.time.LocalDate.now();
    }
}
