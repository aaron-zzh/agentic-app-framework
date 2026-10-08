package com.xuejiai.aaf.module.digitalhuman.vo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/** 数字人积分消耗记录响应。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "数字人积分消耗记录")
public record CreditConsumptionVO(
        @Schema(description = "记录 ID") Long id,
        @Schema(description = "关联的数字人 ID") Long digitalHumanId,
        @Schema(description = "关联的项目 ID") Long projectId,
        @Schema(description = "操作类型：avatar_generation / simple_video / complex_video")
                String operation,
        @Schema(description = "消耗的积分数") Integer creditsConsumed,
        @Schema(description = "成本明细") CostBreakdown breakdown,
        @Schema(description = "创建时间") LocalDateTime createTime) {

    /** 成本明细。 */
    public record CostBreakdown(
            String apiProvider,
            Double apiCost,
            Integer duration,
            Integer sceneCount,
            java.util.List<String> elements) {}
}
