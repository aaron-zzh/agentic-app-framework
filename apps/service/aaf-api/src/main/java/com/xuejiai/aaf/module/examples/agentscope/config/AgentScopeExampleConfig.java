package com.xuejiai.aaf.module.examples.agentscope.config;

import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import com.xuejiai.aaf.framework.intelligent.agentscope.hook.TokenMeteringHook;
import com.xuejiai.aaf.module.examples.agentscope.tools.CalendarTools;
import com.xuejiai.aaf.module.examples.agentscope.tools.MathTools;
import com.xuejiai.aaf.module.examples.agentscope.tools.ObservationHook;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.embedding.dashscope.DashScopeTextEmbedding;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.RAGMode;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.rag.reader.ReaderInput;
import io.agentscope.core.rag.reader.SplitStrategy;
import io.agentscope.core.rag.reader.TextReader;
import io.agentscope.core.rag.store.InMemoryStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tracing.TracerRegistry;
import io.agentscope.core.tracing.telemetry.TelemetryTracer;
import io.agentscope.spring.boot.agui.common.AguiAgentRegistryCustomizer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * AgentScope 示例配置。
 *
 * <p>注册示例所需的 Agent Bean，并将 Agent 接入 AG-UI 协议端点 {@code /agui/context}。 仅在
 * aaf.examples.agentscope.enabled=true 时激活。
 *
 * <p>包含以下能力演示：
 *
 * <ul>
 *   <li>① 基础聊天（basicChatAgent）
 *   <li>⑧ RAG 知识库聊天（ragChatAgent）— 融入基础聊天，加 Knowledge 注入
 *   <li>② 工具调用 + Hook/Tracing（toolCallingAgent）— 含 Langfuse Tracing 可选开关
 *   <li>③ Supervisor 多智能体（supervisorAgent）
 * </ul>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(
        name = "aaf.examples.agentscope.enabled",
        havingValue = "true",
        matchIfMissing = false)
public class AgentScopeExampleConfig {

    /** 从配置或环境变量读取 DashScope API Key */
    @Value("${spring.ai.dashscope.api-key:}")
    private String dashScopeApiKey;

    /**
     * Langfuse Public Key（可选）。 配置后自动启用 Langfuse Tracing，所有 Agent 调用链路上报到 Langfuse。
     * 获取地址：https://cloud.langfuse.com
     */
    @Value("${aaf.examples.agentscope.langfuse.public-key:}")
    private String langfusePublicKey;

    @Value("${aaf.examples.agentscope.langfuse.secret-key:}")
    private String langfuseSecretKey;

    /** Langfuse OTLP 端点，默认使用欧洲区（cloud.langfuse.com），中国用户可自建 */
    @Value(
            "${aaf.examples.agentscope.langfuse.endpoint:https://cloud.langfuse.com/api/public/otel/v1/traces}")
    private String langfuseEndpoint;

    /**
     * 应用启动后初始化 Langfuse Tracing（如已配置）。
     *
     * <p>[Tracing能力点] AgentScope 通过 {@link TracerRegistry} 全局注册 Tracer， 注册后所有 Agent 调用自动上报链路数据（无需修改
     * Agent 代码）。 支持 OpenTelemetry 协议，可对接 Langfuse、Jaeger、Zipkin 等任意 OTLP 后端。
     */
    @PostConstruct
    public void initTracing() {
        if (!StringUtils.hasText(langfusePublicKey) || !StringUtils.hasText(langfuseSecretKey)) {
            log.info(
                    "[Tracing] Langfuse 未配置，跳过 Tracing 初始化。"
                            + "配置 aaf.examples.agentscope.langfuse.public-key/secret-key 启用");
            return;
        }
        // [Tracing能力点] Basic Auth 编码，Langfuse OTLP 认证方式
        String auth =
                Base64.getEncoder()
                        .encodeToString((langfusePublicKey + ":" + langfuseSecretKey).getBytes());
        TelemetryTracer tracer =
                TelemetryTracer.builder()
                        .endpoint(langfuseEndpoint)
                        .addHeader("Authorization", "Basic " + auth)
                        .build();
        // [Tracing能力点] 全局注册，之后所有 Agent 调用自动追踪
        TracerRegistry.register(tracer);
        log.info("[Tracing] Langfuse Tracing 已启用，端点: {}", langfuseEndpoint);
    }

    /** 共享模型 Bean，供各示例 Agent 复用 */
    @Bean("exampleDashScopeModel")
    public Model exampleDashScopeModel() {
        String key =
                StringUtils.hasText(dashScopeApiKey)
                        ? dashScopeApiKey
                        : System.getenv("AI_DASHSCOPE_API_KEY");
        return DashScopeChatModel.builder().apiKey(key).modelName("qwen-plus").build();
    }

    /**
     * RAG 知识库 Bean：内存向量存储 + DashScope Embedding，供 ragChatAgent 使用。
     *
     * <p>[RAG能力点] AgentScope RAG 由三部分组成：
     *
     * <ul>
     *   <li>{@link EmbeddingModel} — 将文本转为向量（此处用 DashScope text-embedding-v3）
     *   <li>VectorStore — 存储和检索向量（此处用 InMemoryStore，生产可换 PgVector/Qdrant）
     *   <li>{@link Knowledge} — 封装上述两者，提供 addDocuments/retrieve 接口
     * </ul>
     */
    @Bean("exampleKnowledge")
    public Knowledge exampleKnowledge() {
        String key =
                StringUtils.hasText(dashScopeApiKey)
                        ? dashScopeApiKey
                        : System.getenv("AI_DASHSCOPE_API_KEY");
        EmbeddingModel embeddingModel =
                DashScopeTextEmbedding.builder()
                        .apiKey(key)
                        .modelName("text-embedding-v3")
                        .dimensions(1024)
                        .build();
        Knowledge knowledge =
                SimpleKnowledge.builder()
                        .embeddingModel(embeddingModel)
                        .embeddingStore(InMemoryStore.builder().dimensions(1024).build())
                        .build();
        // 预置示例文档（实际场景从数据库/文件加载）
        addSampleDocuments(knowledge);
        return knowledge;
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

    /**
     * RAG 聊天 Agent：在基础聊天基础上注入知识库，演示 Generic RAG 模式。
     *
     * <p>[RAG能力点] {@code RAGMode.GENERIC}：每次 LLM 推理前自动检索知识库， 将相关文档注入到 system prompt，无需 Agent
     * 主动调用工具。 对比 {@code RAGMode.AGENTIC}：Agent 通过 retrieve_knowledge 工具主动检索。
     */
    @Bean("ragChatAgent")
    public ReActAgent ragChatAgent(Model exampleDashScopeModel, Knowledge exampleKnowledge) {
        return ReActAgent.builder()
                .name("RAGAssistant")
                .sysPrompt("你是一个有知识库的 AI 助手。请基于知识库内容回答问题，知识库中没有的内容请明确说明。")
                .model(exampleDashScopeModel)
                .memory(new InMemoryMemory())
                .toolkit(new Toolkit())
                // [RAG能力点] 注入知识库，Generic 模式自动检索
                .knowledge(exampleKnowledge)
                .ragMode(RAGMode.GENERIC)
                .retrieveConfig(RetrieveConfig.builder().limit(3).scoreThreshold(0.3).build())
                .build();
    }

    /**
     * 工具调用 Agent：携带数学计算和时间工具。
     *
     * <p>同时演示 AgentScope Hook 机制：
     *
     * <ul>
     *   <li>{@link ObservationHook} — 观察 Hook，记录 Agent 执行全过程（PreCall→PreReasoning→
     *       PostReasoning→PreActing→PostActing→PostCall），演示链路追踪能力点
     *   <li>{@link TokenMeteringHook} — AAF Token 计量 Hook，在 PostCallEvent 读取 getChatUsage()
     *       并记录用量，演示 Hook 与业务系统集成
     * </ul>
     *
     * <p>Hook 执行顺序由 {@link io.agentscope.core.hook.Hook#priority()} 决定，数值越小优先级越高。
     * TokenMeteringHook(200) 先于 ObservationHook(900) 执行。
     */
    @Bean("toolCallingAgent")
    public ReActAgent toolCallingAgent(
            Model exampleDashScopeModel,
            MathTools mathTools,
            ObservationHook observationHook,
            TokenMeteringHook tokenMeteringHook) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(mathTools);
        return ReActAgent.builder()
                .name("ToolAgent")
                .sysPrompt("你是一个能使用工具的助手。需要计算时请使用 calculate 工具，需要查询时间时请使用 get_current_time 工具。")
                .model(exampleDashScopeModel)
                .toolkit(toolkit)
                .memory(new InMemoryMemory())
                // [Hook能力点] 注册多个 Hook，按 priority() 顺序执行
                .hook(tokenMeteringHook) // priority=200，Token 计量
                .hook(observationHook) // priority=900，链路观察
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

    /** Supervisor Agent：通过 subAgent 委托给日历子 Agent，作为 A2A 对外暴露的主 Agent */
    @Primary
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
     *
     * <ul>
     *   <li>{@code POST /agui/context} — 使用默认 Agent（basic）
     *   <li>{@code POST /agui/context/basic} — 基础聊天
     *   <li>{@code POST /agui/context/tool} — 工具调用
     * </ul>
     */
    @Bean
    public AguiAgentRegistryCustomizer exampleAguiAgentRegistryCustomizer(
            Model exampleDashScopeModel, MathTools mathTools) {
        return registry -> {
            // 基础聊天 Agent（AG-UI 默认）
            registry.registerFactory(
                    "basic",
                    () ->
                            ReActAgent.builder()
                                    .name("BasicAssistant")
                                    .sysPrompt("你是一个友好、简洁的 AI 助手。")
                                    .model(exampleDashScopeModel)
                                    .memory(new InMemoryMemory())
                                    .toolkit(new Toolkit())
                                    .build());

            // 工具调用 Agent
            registry.registerFactory(
                    "tool",
                    () -> {
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

    /** 向知识库添加示例文档（演示用，实际场景从数据库/文件加载） */
    private void addSampleDocuments(Knowledge knowledge) {
        TextReader reader = new TextReader(512, SplitStrategy.PARAGRAPH, 50);
        List<String> docs =
                List.of(
                        "AAF（Agentic App Framework）是生产级 AI 原生多智能体应用开发框架。"
                                + "核心能力：多智能体协作、工作流引擎、知识库管理、规范驱动开发。"
                                + "技术栈：Java 25、Spring Boot 4、Spring AI、AgentScope。",
                        "AgentScope 是阿里巴巴开源的多智能体框架，提供 ReActAgent、Pipeline、MsgHub、"
                                + "Session、Hook、RAG、Plan、MCP 等核心能力。"
                                + "Java 版本基于 Project Reactor 实现响应式编程。",
                        "RAG（检索增强生成）是一种将知识库检索与 LLM 生成结合的技术。"
                                + "AgentScope 支持 Generic 模式（自动注入）和 Agentic 模式（工具主动检索）。"
                                + "向量存储支持 InMemoryStore、PgVector、Qdrant 等。");
        for (String text : docs) {
            try {
                List<Document> documents = reader.read(ReaderInput.fromString(text)).block();
                if (documents != null) {
                    knowledge.addDocuments(documents).block();
                }
            } catch (Exception e) {
                log.warn("添加示例文档失败: {}", e.getMessage());
            }
        }
    }
}
