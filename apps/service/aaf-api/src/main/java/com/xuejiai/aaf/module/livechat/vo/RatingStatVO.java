package com.xuejiai.aaf.module.livechat.vo;

import java.util.Map;

/** 评价统计 VO。 */
public record RatingStatVO(
        double avgScore,
        long totalCount,
        Map<Integer, Long> scoreDistribution) {}
