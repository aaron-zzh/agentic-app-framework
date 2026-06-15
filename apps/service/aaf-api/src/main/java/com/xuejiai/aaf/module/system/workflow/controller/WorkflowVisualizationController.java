package com.xuejiai.aaf.module.system.workflow.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.engine.workflow.runtime.WorkflowExecutionLog;
import com.xuejiai.aaf.framework.engine.workflow.runtime.WorkflowExecutionLogger;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 工作流可视化数据端点——提供执行轨迹和时间线数据。
 *
 * @author AaronZZH
 */
@Tag(name = "工作流可视化")
@RestController
@RequestMapping("/api/workflow/instances")
@RequiredArgsConstructor
public class WorkflowVisualizationController {

    private final WorkflowExecutionLogger executionLogger;

    @Operation(summary = "获取执行轨迹（节点状态列表+耗时）")
    @GetMapping("/{id}/execution-trace")
    public Result<List<WorkflowExecutionLog>> getExecutionTrace(@PathVariable String id) {
        return Result.success(executionLogger.getExecutionLogs(id));
    }

    @Operation(summary = "获取时间线数据")
    @GetMapping("/{id}/timeline")
    public Result<List<TimelineEntry>> getTimeline(@PathVariable String id) {
        var logs = executionLogger.getExecutionLogs(id);
        var timeline =
                logs.stream()
                        .map(
                                l ->
                                        new TimelineEntry(
                                                l.nodeId(),
                                                l.nodeName(),
                                                l.status(),
                                                l.durationMs(),
                                                l.timestamp().toEpochMilli()))
                        .toList();
        return Result.success(timeline);
    }

    /** 时间线条目 */
    public record TimelineEntry(
            String nodeId, String nodeName, String status, long durationMs, long timestampMs) {}
}
