package com.xuejiai.aaf.module.livechat.vo;

import java.util.Map;

/** 工单统计 VO。 */
public record TicketStatVO(
        long total,
        long closed,
        long overdue,
        double resolveRate,
        double overdueRate,
        Map<String, Long> statusDistribution,
        Map<String, Long> typeDistribution) {}
