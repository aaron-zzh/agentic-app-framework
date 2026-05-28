package com.xuejiai.aaf.framework.engine.workflow.runtime;

import java.time.Instant;

/**
 * 工作流节点执行日志记录。
 *
 * @param nodeId 节点标识
 * @param nodeName 节点名称
 * @param input 节点输入
 * @param output 节点输出
 * @param durationMs 执行耗时（毫秒）
 * @param status 执行状态：running/completed/failed
 * @param error 错误信息（失败时）
 * @param timestamp 时间戳
 */
public record WorkflowExecutionLog(
        String nodeId,
        String nodeName,
        String input,
        String output,
        long durationMs,
        String status,
        String error,
        Instant timestamp) {
}
