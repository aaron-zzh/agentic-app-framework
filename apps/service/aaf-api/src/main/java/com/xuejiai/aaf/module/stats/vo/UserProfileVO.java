package com.xuejiai.aaf.module.stats.vo;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/** 用户画像聚合结果。 */
@Schema(description = "用户画像")
public record UserProfileVO(
        @Schema(description = "活跃度分布：高/中/低 → 人数") Map<String, Long> activityDistribution,
        @Schema(description = "偏好功能 TOP N") List<FeatureUsage> topFeatures,
        @Schema(description = "使用时段分布（0-23小时 → 次数）") Map<Integer, Long> hourlyDistribution) {
    @Schema(description = "功能使用统计")
    public record FeatureUsage(
            @Schema(description = "功能/页面") String feature,
            @Schema(description = "使用次数") long count) {}
}
