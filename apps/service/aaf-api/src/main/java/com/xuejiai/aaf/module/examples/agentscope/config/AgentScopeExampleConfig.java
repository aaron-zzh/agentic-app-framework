package com.xuejiai.aaf.module.examples.agentscope.config;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.xuejiai.aaf.module.examples.agentscope.tools.CalendarTools;
import com.xuejiai.aaf.module.examples.agentscope.tools.MathTools;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tracing.TracerRegistry;
import io.agentscope.core.tracing.telemetry.TelemetryTracer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * AgentScope 示例配置（v2 兼容版）。
 *
 * <p>覆盖可运行示例：
 *
 * <ul>
 *   <li>① 基础聊天（basicChatAgent）
 *   <li>② 工具调用（toolCallingAgent）
 *   <li>③ Supervisor 多智能体（supervisorAgent）
 *   <li>④ Pipeline（sqlGeneratorAgent + sqlRaterAgent，见 PipelineExampleConfig）
 *   <li>⑦ MCP 工具（mcpToolAgent，见 McpExampleConfig）
 * </ul>
 *
 * <p>已移除（v2 不再支持）：RAG、Plan、MsgHub、JsonSession、RealtimeTTS。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(
        name = "aaf.examples.agentscope.enabled",
        havingValue = "true",
        matchIfMissing = false)
public class AgentScopeExampleConfig {

    @Value("${spring.ai.dashscope.api-key:}")
    private String dashScopeApiKey;

    @Value("${aaf.examples.agentscope.langfuse.public-key:}")
    private String langfusePublicKey;

    @Value("${aaf.examples.agentscope.langfuse.secret-key:}")
    private String langfuseSecretKey;

    @Value(
            "${aaf.examples.agentscope.langfuse.endpoint:https://cloud.langfuse.com/api/public/otel/v1/traces}")
    private String langfuseEndpoint;

    @PostConstruct
    public void initTracing() {
        if (!StringUtils.hasText(langfusePublicKey) || !StringUtils.hasText(langfuseSecretKey)) {
            log.info("[Tracing] Langfuse 未配置，跳过 Tracing 初始化");
            return;
        }
        String auth =
                Base64.getEncoder()
                        .encodeToString((langfusePublicKey + ":" + langfuseSecretKey).getBytes());
        TelemetryTracer tracer =
                TelemetryTracer.builder()
                        .endpoint(langfuseEndpoint)
                        .addHeader("Authorization", "Basic " + auth)
                        .build();
        TracerRegistry.register(tracer);
        log.info("[Tracing] Langfuse Tracing 已启用，端点: {}", langfuseEndpoint);
    }

    @Bean("exampleDashScopeModel")
    public Model exampleDashScopeModel() {
        String key =
                StringUtils.hasText(dashScopeApiKey)
                        ? dashScopeApiKey
                        : System.getenv("AI_DASHSCOPE_API_KEY");
        return DashScopeChatModel.builder().apiKey(key).modelName("qwen-plus").build();
    }

    /** ① 基础聊天 Agent */
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

    /** ② 工具调用 Agent */
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

    /** ③ Supervisor Agent */
    @Bean("supervisorAgent")
    public ReActAgent supervisorAgent(
            Model exampleDashScopeModel,
            @org.springframework.beans.factory.annotation.Qualifier("calendarSubAgent")
                    ReActAgent calendarSubAgent) {
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
}
