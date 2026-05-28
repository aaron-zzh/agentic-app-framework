package com.xuejiai.aaf.module.examples.agentscope.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.Model;
import io.agentscope.core.plan.PlanNotebook;
import io.agentscope.core.plan.storage.InMemoryPlanStorage;
import io.agentscope.core.tool.Toolkit;

/**
 * Plan 规划示例配置。
 *
 * <p>演示 AgentScope {@link PlanNotebook} 能力：Agent 将复杂任务分解为子任务，
 * 逐步执行并追踪进度，适合需要多步骤规划的场景（如写代码、搭建系统、研究报告等）。
 *
 * <p>PlanNotebook 工作原理：
 *
 * <ol>
 *   <li>Agent 收到复杂任务后，调用 {@code create_plan} 工具创建计划（含子任务列表）
 *   <li>每次 LLM 推理前，PlanNotebook 自动注入 {@code <system-hint>} 提示当前进度
 *   <li>Agent 按子任务顺序执行，调用 {@code finish_subtask} 标记完成
 *   <li>所有子任务完成后调用 {@code finish_plan} 结束计划
 * </ol>
 *
 * <p>PlanNotebook 提供 10 个工具函数：create_plan、update_plan_info、revise_current_plan、
 * update_subtask_state、finish_subtask、view_subtasks、get_subtask_count、
 * finish_plan、view_historical_plans、recover_historical_plan。
 *
 * <p>仅在 aaf.examples.agentscope.enabled=true 时激活。
 */
@Configuration
@ConditionalOnProperty(
        name = "aaf.examples.agentscope.enabled",
        havingValue = "true",
        matchIfMissing = false)
public class PlanExampleConfig {

    /**
     * Plan Agent：启用 PlanNotebook，自动获得任务规划和追踪能力。
     *
     * <p>[Plan能力点] {@code .enablePlan()} 等价于注册默认 PlanNotebook， 自动向 Toolkit 注册 10 个 Plan 工具函数，并注册
     * Hook 在每次推理前注入进度提示。 也可用 {@code .planNotebook(PlanNotebook.builder()...build())} 自定义配置。
     */
    @Bean("planAgent")
    public ReActAgent planAgent(Model exampleDashScopeModel) {
        // [Plan能力点] 自定义 PlanNotebook：内存存储，最多 10 个子任务
        PlanNotebook planNotebook =
                PlanNotebook.builder().storage(new InMemoryPlanStorage()).maxSubtasks(10).build();

        return ReActAgent.builder()
                .name("PlanAssistant")
                .sysPrompt(
                        "你是一个擅长任务规划的 AI 助手。"
                                + "收到复杂任务时，先创建计划并分解为子任务，然后逐步执行。"
                                + "每完成一个子任务及时标记，保持进度清晰。")
                .model(exampleDashScopeModel)
                .toolkit(new Toolkit())
                .memory(new InMemoryMemory())
                // [Plan能力点] 注入 PlanNotebook，自动注册规划工具和进度提示 Hook
                .planNotebook(planNotebook)
                .build();
    }
}
