package com.xuejiai.aaf.framework.engine.task;

/**
 * 任务事件总线接口——持久化 + 实时广播。
 *
 * <p>业务层提供实现（写库 + SSE/WS 推送）； framework 层通过此接口发布事件，不感知传输细节。
 *
 * @author Kiro
 */
public interface TaskEventBus {

    /**
     * 发布任务事件。
     *
     * @param taskId 业务任务 ID
     * @param executionId 执行实例 ID
     * @param subtaskKey 子任务标识（主任务传 null）
     * @param type 事件类型（task_started / subtask_completed / error 等）
     * @param payloadJson 事件载荷 JSON
     */
    void publish(Long taskId, Long executionId, String subtaskKey, String type, String payloadJson);
}
