package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.assistant.application.InvocationProfile.AgentKind;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent.DecompositionStrategy;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillBinding;

/**
 * 内置系统技能的适用范围（ADR-008）。
 *
 * <p>{@code ai_system_skill_binding} 没有智能体类型维度，三个任务类内置技能的适用范围因此由 harness 在解析激活技能时按智能体类型与容器状态过滤，
 * 与控制工具的可见性保持一致——技能正文只是指导，工具可见性才是强制边界，两者不得出现"技能提到的工具模型看不到"。
 *
 * <ul>
 *   <li>{@code builtin-task-triage}：前注意判断，协调者的两种容器状态都适用
 *   <li>{@code builtin-step-planning}：节点内拆步骤，依赖持久化的 ExecutorPlan，只在任务态内的协调者与执行者可用； 分解策略为 {@code
 *       REQUIRED} 时协调者看不到 {@code plan_steps}/{@code report_step}，该技能随之不激活
 *   <li>{@code builtin-agent-delegation}：委派子智能体，只在任务态内的协调者可用；分解策略为 {@code NONE} 时协调者看不到 {@code
 *       delegate_agents}，该技能随之不激活
 * </ul>
 *
 * 其余系统技能不受限制，原样保留。分解策略随根命令冻结并被各节点继承，但只改变协调者的工具面，执行者不受影响。
 */
final class BuiltinSkillScope {

    static final String TASK_TRIAGE = "builtin-task-triage";
    static final String STEP_PLANNING = "builtin-step-planning";
    static final String AGENT_DELEGATION = "builtin-agent-delegation";

    private BuiltinSkillScope() {}

    /** 过滤掉不适用于当前智能体类型、容器状态与分解策略的内置技能绑定。 */
    static List<SkillBinding> filter(
            List<SkillBinding> bindings,
            AgentKind agentKind,
            boolean taskState,
            DecompositionStrategy decomposition) {
        Objects.requireNonNull(bindings, "bindings 不能为空");
        Objects.requireNonNull(agentKind, "agentKind 不能为空");
        Objects.requireNonNull(decomposition, "decomposition 不能为空");
        return bindings.stream()
                .filter(
                        binding ->
                                applicable(binding.skillKey(), agentKind, taskState, decomposition))
                .toList();
    }

    static boolean applicable(
            String skillKey,
            AgentKind agentKind,
            boolean taskState,
            DecompositionStrategy decomposition) {
        return switch (skillKey) {
            case TASK_TRIAGE -> agentKind == AgentKind.COORDINATOR;
            case STEP_PLANNING ->
                    taskState
                            && ((agentKind == AgentKind.COORDINATOR
                                            && decomposition != DecompositionStrategy.REQUIRED)
                                    || agentKind == AgentKind.EXECUTOR);
            case AGENT_DELEGATION ->
                    taskState
                            && agentKind == AgentKind.COORDINATOR
                            && decomposition != DecompositionStrategy.NONE;
            default -> true;
        };
    }
}
