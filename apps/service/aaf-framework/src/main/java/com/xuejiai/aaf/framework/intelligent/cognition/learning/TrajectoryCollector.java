package com.xuejiai.aaf.framework.intelligent.cognition.learning;

import java.util.List;

/**
 * 轨迹采集器——收集 Agent 执行过程的完整轨迹。
 *
 * <p>对齐设计图"Learning 横切反哺通道"中的 TrajectoryCollector 节点。 采集内容：输入、工具调用、中间推理、最终输出。 P2 占位：后续由
 * PostExecutionHook 触发。
 */
public interface TrajectoryCollector {

    /** 执行轨迹记录 */
    record Trajectory(
            String executionId,
            String agentId,
            Long userId,
            String input,
            String output,
            List<ToolCall> toolCalls,
            boolean success,
            long durationMs) {}

    /** 工具调用记录 */
    record ToolCall(String toolName, String arguments, String result) {}

    /**
     * 采集一次执行轨迹。
     *
     * @param trajectory 轨迹数据
     */
    void collect(Trajectory trajectory);
}
