package com.xuejiai.aaf.framework.engine.workflow.runtime;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 工作流执行日志记录器——记录每个节点的执行状态和耗时。
 *
 * <p>按 processInstanceId 分组存储日志，支持实时查询。
 */
@Slf4j
@Component
public class WorkflowExecutionLogger {

    /** processInstanceId → 执行日志列表 */
    private final Map<String, List<WorkflowExecutionLog>> logs = new ConcurrentHashMap<>();

    /** processInstanceId → nodeId → 开始时间 */
    private final Map<String, Map<String, Long>> startTimes = new ConcurrentHashMap<>();

    /**
     * 记录节点开始执行。
     */
    public void logNodeStart(String processInstanceId, String nodeId, String nodeName, String input) {
        startTimes.computeIfAbsent(processInstanceId, k -> new ConcurrentHashMap<>())
                .put(nodeId, System.currentTimeMillis());

        var logEntry = new WorkflowExecutionLog(
                nodeId, nodeName, input, null, 0, "running", null, Instant.now());
        logs.computeIfAbsent(processInstanceId, k -> new CopyOnWriteArrayList<>()).add(logEntry);

        log.debug("节点开始执行: processId={}, nodeId={}, nodeName={}", processInstanceId, nodeId, nodeName);
    }

    /**
     * 记录节点执行完成。
     */
    public void logNodeComplete(String processInstanceId, String nodeId, String nodeName, String output) {
        var duration = calculateDuration(processInstanceId, nodeId);
        var logEntry = new WorkflowExecutionLog(
                nodeId, nodeName, null, output, duration, "completed", null, Instant.now());
        logs.computeIfAbsent(processInstanceId, k -> new CopyOnWriteArrayList<>()).add(logEntry);

        log.debug("节点执行完成: processId={}, nodeId={}, duration={}ms", processInstanceId, nodeId, duration);
    }

    /**
     * 记录节点执行失败。
     */
    public void logNodeFailed(String processInstanceId, String nodeId, String nodeName, String error) {
        var duration = calculateDuration(processInstanceId, nodeId);
        var logEntry = new WorkflowExecutionLog(
                nodeId, nodeName, null, null, duration, "failed", error, Instant.now());
        logs.computeIfAbsent(processInstanceId, k -> new CopyOnWriteArrayList<>()).add(logEntry);

        log.warn("节点执行失败: processId={}, nodeId={}, error={}", processInstanceId, nodeId, error);
    }

    /**
     * 获取指定流程实例的所有执行日志。
     */
    public List<WorkflowExecutionLog> getExecutionLogs(String processInstanceId) {
        return logs.getOrDefault(processInstanceId, List.of());
    }

    private long calculateDuration(String processInstanceId, String nodeId) {
        var times = startTimes.get(processInstanceId);
        if (times == null) return 0;
        var startTime = times.remove(nodeId);
        return startTime != null ? System.currentTimeMillis() - startTime : 0;
    }
}
