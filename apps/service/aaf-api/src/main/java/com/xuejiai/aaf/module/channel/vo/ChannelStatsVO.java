package com.xuejiai.aaf.module.channel.vo;

/**
 * 渠道状态统计 VO。
 */
public record ChannelStatsVO(
        Long configId,
        String name,
        String channelType,
        boolean available,
        long totalMessages,
        long errorMessages,
        double errorRate) {}
