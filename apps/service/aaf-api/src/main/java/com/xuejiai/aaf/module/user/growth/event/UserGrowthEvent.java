package com.xuejiai.aaf.module.user.growth.event;

/**
 * 用户成长任务触发事件。
 *
 * <p>各业务模块在关键节点发布此事件，由 {@link UserGrowthEventListener} 统一处理进度推进。
 */
public record UserGrowthEvent(Long userId, String eventCode) {}
