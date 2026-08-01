package com.xuejiai.aaf.module.ui.tracking;

import java.time.LocalDateTime;

/**
 * 用户行为追踪事件（内存存储模型）。
 *
 * <p>M19：{@code orgId} 为采集时的组织上下文，热力图/模式聚合据此按组织过滤， 避免组织管理员读取跨组织行为数据；NULL 表示无组织上下文采集，仅平台管理员可见。
 */
public record UserTrackingEvent(
        Long orgId,
        String type,
        String page,
        String target,
        Integer x,
        Integer y,
        Long timestamp,
        String extra,
        LocalDateTime receivedAt) {}
