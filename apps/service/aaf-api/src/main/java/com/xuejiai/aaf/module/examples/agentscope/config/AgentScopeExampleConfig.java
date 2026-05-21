package com.xuejiai.aaf.module.examples.agentscope.config;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.spring.boot.agui.common.AguiAgentRegistryCustomizer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.xuejiai.aaf.module.examples.agentscope.tools.CalendarTools;
import com.xuejiai.aaf.module.examples.agentscope.tools.MathTools;

/**
 * AgentScope 示例配置。
 *
 * <p>注册示例所需的 Agent Bean，并将 Agent 接入 AG-UI 协议端点 {@code /agui/run}。
 * 仅在 aaf.examples.agentscope.enabled=true 时激活。
 */
@Configuration
@ConditionalOnProperty(name = "aaf.examples.agentscope.enabled", havingValue = "true", matchIfMissing = false)
public class AgentScopeExampleConfig {

    /** 从配置或环境变量读取 DashScope API Key */
    @Value("${spring.ai.dashscope.api-key:}")
    private String dashScopeApiKey;

    /** 共享模型 Bean，供各示例 Agent 复用 */
    @Bean("exampleDashScopeModel")
    public Model exampleDashScopeModel() {
        String key = StringUtils.hasText(dashScopeApiKey)
                ? dashScopeApiKey
                : System.getenv("AI_DASHSCOPE_API_KEY");
        return DashScopeChatModel.builder()
                .apiKey(key)
                .modelName("qwen-plus")
                .build();
    }

    /** 基础聊天 Agent：无工具，仅对话 */
    @Bean("basicChatAgent")
    public ReActAgent basicChatAgent(Model exampleDashScopeModel) {
        return ReActAgent.builder()
                .name("BasicAssistant")
                .sysPrompt("你是一个友好、简洁的 AI 助手。")
                .model(exampleDashScopeModel)
                .memory(new InMemoryMemory())
                .toolkit(new Toolkit())
                .build();
    }

    /** 工具调用 Agent：携带数学计算和时间工具 */
    @Bean("toolCallingAgent")
    public ReActAgent toolCallingAgent(Model exampleDashScopeModel, MathTools mathTools) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(mathTools);
        return ReActAgent.builder()
                .name("ToolAgent")
                .sysPrompt("你是一个能使用工具的助手。需要计算时请使用 calculate 工具，需要查询时间时请使用 get_current_time 工具。")
                .model(exampleDashScopeModel)
                .toolkit(toolkit)
                .memory(new InMemoryMemory())
                .build();
    }

    /** 日历子 Agent */
    @Bean("calendarSubAgent")
    public ReActAgent calendarSubAgent(Model exampleDashScopeModel, CalendarTools calendarTools) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(calendarTools);
        return ReActAgent.builder()
                .name("schedule_event")
                .description("日历助手，负责查询和创建日程")
                .sysPrompt("你是日历助手，负责管理日程安排。使用工具查询可用时间并创建日程。")
                .model(exampleDashScopeModel)
                .toolkit(toolkit)
                .memory(new InMemoryMemory())
                .build();
    }

    /** Supervisor Agent：通过 subAgent 委托给日历子 Agent */
    @Bean("supervisorAgent")
    public ReActAgent supervisorAgent(Model exampleDashScopeModel, ReActAgent calendarSubAgent) {
        Toolkit toolkit = new Toolkit();
        toolkit.registration().subAgent(() -> calendarSubAgent).apply();
        return ReActAgent.builder()
                .name("Supervisor")
                .sysPrompt("你是个人助理，可以安排日程。将任务委托给合适的子 Agent 处理。")
                .model(exampleDashScopeModel)
                .toolkit(toolkit)
                .memory(new InMemoryMemory())
                .build();
    }

    /**
     * 将示例 Agent 注册到 AG-UI 协议端点。
     *
     * <p>注册后可通过以下方式访问：
     * <ul>
     *   <li>{@code POST /agui/run} — 使用默认 Agent（basic）</li>
     *   <li>{@code POST /agui/run/basic} — 基础聊天</li>
     *   <li>{@code POST /agui/run/tool} — 工具调用</li>
     * </ul>
     */
    @Bean
    public AguiAgentRegistryCustomizer exampleAguiAgentRegistryCustomizer(
            Model exampleDashScopeModel, MathTools mathTools) {
        return registry -> {
            // 基础聊天 Agent（AG-UI 默认）
            registry.registerFactory("basic", () -> ReActAgent.builder()
                    .name("BasicAssistant")
                    .sysPrompt("你是一个友好、简洁的 AI 助手。")
                    .model(exampleDashScopeModel)
                    .memory(new InMemoryMemory())
                    .toolkit(new Toolkit())
                    .build());

            // 工具调用 Agent
            registry.registerFactory("tool", () -> {
                Toolkit toolkit = new Toolkit();
                toolkit.registerTool(mathTools);
                return ReActAgent.builder()
                        .name("ToolAgent")
                        .sysPrompt("你是一个能使用工具的助手，可以进行数学计算和时间查询。")
                        .model(exampleDashScopeModel)
                        .toolkit(toolkit)
                        .memory(new InMemoryMemory())
                        .build();
            });
        };
    }
}
