package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent.DecompositionStrategy;

/** 按智能体类型、容器状态与分解策略限定内置控制工具，并提供 Task 合同的可信工具上界。 */
public final class BuiltinControlToolProfile {

    private static final List<ToolRef> DIRECT_COORDINATOR_TOOLS =
            List.of(
                    toolRef(InspectTasksTool.TOOL_NAME),
                    toolRef(PromoteDirectTaskTool.TOOL_NAME),
                    toolRef(AmendTaskTool.TOOL_NAME),
                    toolRef(PauseTaskTool.TOOL_NAME),
                    toolRef(ResumeTaskTool.TOOL_NAME),
                    toolRef(TakeOverTaskTool.TOOL_NAME),
                    toolRef(HandBackTaskTool.TOOL_NAME),
                    toolRef(CancelTaskTool.TOOL_NAME));

    private static final List<ToolRef> TASK_EXECUTION_TOOLS =
            List.of(
                    toolRef(SubmitExecutorPlanTool.TOOL_NAME),
                    toolRef(ReportExecutorStepTool.TOOL_NAME),
                    toolRef(RequestClarificationTool.TOOL_NAME));

    private static final List<ToolRef> TASK_DELEGATION_TOOLS =
            List.of(
                    toolRef(SubmitCoordinationPlanTool.TOOL_NAME),
                    toolRef(RequestClarificationTool.TOOL_NAME));

    private static final List<ToolRef> TASK_AUTO_TOOLS =
            List.of(
                    toolRef(SubmitCoordinationPlanTool.TOOL_NAME),
                    toolRef(SubmitExecutorPlanTool.TOOL_NAME),
                    toolRef(ReportExecutorStepTool.TOOL_NAME),
                    toolRef(RequestClarificationTool.TOOL_NAME));

    private static final Set<String> ALL_TOOL_NAMES =
            Stream.concat(DIRECT_COORDINATOR_TOOLS.stream(), TASK_AUTO_TOOLS.stream())
                    .map(ToolRef::name)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private BuiltinControlToolProfile() {}

    /** 返回当前物理 Agent 调用实际可见的内置控制工具。 */
    public static List<ToolRef> resolve(AssistantCommand command) {
        var agentKind = command.invocationProfile().agentKind();
        if (command.taskId() == null) {
            return agentKind == InvocationProfile.AgentKind.COORDINATOR
                    ? DIRECT_COORDINATOR_TOOLS
                    : List.of();
        }
        if (command.nodeIdentity() == null) {
            return List.of();
        }
        return switch (agentKind) {
            case COORDINATOR ->
                    switch (command.invocationProfile().executionIntent().decomposition()) {
                        case DECOMPOSE_AUTO -> TASK_AUTO_TOOLS;
                        case DECOMPOSE_NONE -> TASK_EXECUTION_TOOLS;
                        case DECOMPOSE_REQUIRED -> TASK_DELEGATION_TOOLS;
                    };
            case EXECUTOR -> TASK_EXECUTION_TOOLS;
            case AGGREGATOR -> List.of(toolRef(RequestClarificationTool.TOOL_NAME));
        };
    }

    /** 返回整个 Task 生命周期可能暴露的内置控制工具名。强制或自动分解可能产生执行者与聚合者，合同需覆盖后续节点；禁止分解时只保留单 Agent 执行工具。 */
    public static Set<String> contractActions(DecompositionStrategy decomposition) {
        var tools =
                decomposition == DecompositionStrategy.DECOMPOSE_NONE
                        ? TASK_EXECUTION_TOOLS
                        : TASK_AUTO_TOOLS;
        return tools.stream()
                .map(ToolRef::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static boolean contains(String toolName) {
        return ALL_TOOL_NAMES.contains(toolName);
    }

    private static ToolRef toolRef(String toolName) {
        return new ToolRef(toolName, 1, toolName);
    }
}
