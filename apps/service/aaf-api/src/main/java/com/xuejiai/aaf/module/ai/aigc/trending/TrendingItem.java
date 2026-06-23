package com.xuejiai.aaf.module.ai.aigc.trending;

/** 单条热点条目。 */
public record TrendingItem(
        /** 序号（1-based） */
        int rank,
        /** 热点标题 */
        String title,
        /** 简短摘要（1-2句） */
        String summary,
        /** 热度标签，如 "爆款"/"上升" */
        String tag,
        /** 内容创作借势建议（1句话） */
        String suggestion) {}
