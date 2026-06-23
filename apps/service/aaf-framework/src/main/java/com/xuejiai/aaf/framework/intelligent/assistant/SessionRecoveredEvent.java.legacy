package com.xuejiai.aaf.framework.intelligent.assistant;

/**
 * 会话恢复事件——服务重启后恢复活跃会话时发布。
 *
 * @param sessionId 恢复的会话 ID
 * @param userId 所属用户 ID
 * @param recoveredTaskCount 恢复的任务数量
 * @param message 恢复描述信息
 */
public record SessionRecoveredEvent(
        String sessionId, Long userId, int recoveredTaskCount, String message) {}
