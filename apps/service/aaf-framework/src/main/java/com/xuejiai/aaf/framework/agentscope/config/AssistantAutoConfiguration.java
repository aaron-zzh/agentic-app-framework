/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.JdbcTemplate;

import com.xuejiai.aaf.framework.agentscope.ContentCreationBootstrap;
import com.xuejiai.aaf.framework.agentscope.agent.ContentCreationAgentFactory;
import com.xuejiai.aaf.framework.agentscope.gateway.HarnessGateway;
import com.xuejiai.aaf.framework.agentscope.runtime.AafAgentServices;
import com.xuejiai.aaf.framework.agentscope.runtime.AafContextHolder;
import com.xuejiai.aaf.framework.agentscope.runtime.AssistantContextLoader;
import com.xuejiai.aaf.framework.agentscope.runtime.KbAutoInjectLoader;
import com.xuejiai.aaf.framework.agentscope.runtime.PersonaContextLoader;
import com.xuejiai.aaf.framework.agentscope.runtime.SkillContextLoader;
import com.xuejiai.aaf.framework.agentscope.session.SessionAgentManager;
import com.xuejiai.aaf.framework.agentscope.session.SessionStore;
import com.xuejiai.aaf.framework.agentscope.session.tool.SessionsTool;
import com.xuejiai.aaf.framework.agentscope.tool.GenerateImageTool;
import com.xuejiai.aaf.framework.agentscope.tool.GenerateMusicTool;
import com.xuejiai.aaf.framework.agentscope.tool.GenerateVideoTool;
import com.xuejiai.aaf.framework.agentscope.tool.WeatherAgentTool;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingService;
import com.xuejiai.aaf.framework.engine.knowledge.importer.ImporterFactory;
import com.xuejiai.aaf.framework.engine.knowledge.search.SimilaritySearchService;
import com.xuejiai.aaf.framework.engine.memory.AtomMemoryEngine;
import com.xuejiai.aaf.framework.intelligent.ai.chat.AiProperties;
import com.xuejiai.aaf.framework.intelligent.ai.ocr.OcrServiceFactory;
import com.xuejiai.aaf.framework.intelligent.assistant.hitl.HumanApprovalService;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.messaging.MessageService;

import io.agentscope.core.agent.Agent;
import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import io.agentscope.harness.agent.gateway.ChannelManager;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import io.agentscope.spring.boot.agui.common.AguiAgentId;
import io.micrometer.context.ThreadLocalAccessor;

/**
 * AAF AI 助理 Agent 自动装配。
 *
 * <p>注册后端点 {@code POST /api/agui/run/assistant}（默认）、{@code /api/agui/run/editor}、 {@code
 * /api/agui/run/customer-service} 立即可用。
 */
@AutoConfiguration
@ConditionalOnClass(io.agentscope.harness.agent.HarnessAgent.class)
@ConditionalOnProperty(
        prefix = "aaf.agentscope.assistant",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(ContentCreationProperties.class)
public class AssistantAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AssistantAutoConfiguration.class);

    public AssistantAutoConfiguration(ContentCreationProperties props) {
        log.info(
                "[ContentCreation] 自动装配启用 mainAgentId={} editorAgentId={} sandbox={}",
                props.getMainAgentId(),
                props.getEditorAgentId(),
                props.getSandboxType());

        // Dev mode fallback：当配置了 dev-mode-user-id 时，把全局兜底上下文塞进 AafContextHolder
        // 让 curl/Postman 等无 forwardedProps 的请求也能跑通工具链。
        // 生产环境必须留 null，由 AafAguiV2RestController 按请求填充 thread-local。
        if (props.getDevModeUserId() != null) {
            var fallback =
                    new AafContextHolder.AafContext(
                            props.getDevModeUserId(),
                            null,
                            props.getDevModeConversationId(),
                            props.getDevModeKnowledgeBaseId(),
                            null);
            AafContextHolder.setDevModeFallback(fallback);
            log.warn(
                    "[ContentCreation] ⚠️ Dev mode fallback context 已激活 userId={} convId={} kbId={}"
                            + "（生产环境请把 aaf.agentscope.content-creation.dev-mode-user-id 设为空）",
                    props.getDevModeUserId(),
                    props.getDevModeConversationId(),
                    props.getDevModeKnowledgeBaseId());
        }
    }

    /** AAF 业务服务集合 Bean。 */
    @Bean
    public AafAgentServices aafAgentServices(
            EmbeddingService embeddingService,
            AtomMemoryEngine memoryEngine,
            SimilaritySearchService kbSearch,
            HumanApprovalService humanApprovalService,
            JdbcTemplate jdbcTemplate,
            CapabilityRouter capabilityRouter,
            AiCreditGuard creditGuard,
            AiModelRepository modelRepository,
            OcrServiceFactory ocrServiceFactory,
            MessageService messageService,
            ImporterFactory importerFactory) {
        return new AafAgentServices(
                embeddingService,
                memoryEngine,
                kbSearch,
                humanApprovalService,
                jdbcTemplate,
                capabilityRouter,
                creditGuard,
                modelRepository,
                ocrServiceFactory,
                messageService,
                importerFactory);
    }

    /**
     * 基础设施装配器 Bean——单例，所有下游 singleton Bean 由它派生。
     *
     * <p>用 Spring 单例语义保证整个 ApplicationContext 只装配一次（避免多次创建 SqliteBaseStore 文件锁等冲突）。
     */
    @Bean
    public ContentCreationBootstrap.Infrastructure contentCreationInfrastructure(
            ContentCreationProperties props) {
        return ContentCreationBootstrap.assemble(props);
    }

    @Bean
    public BaseStore agentscopeBaseStore(ContentCreationBootstrap.Infrastructure infra) {
        return infra.baseStore();
    }

    @Bean
    public WorkspaceManager agentscopeWorkspaceManager(
            ContentCreationBootstrap.Infrastructure infra) {
        return infra.workspaceManager();
    }

    @Bean
    public SessionStore agentscopeSessionStore(ContentCreationBootstrap.Infrastructure infra) {
        return infra.sessionStore();
    }

    @Bean
    public SessionAgentManager agentscopeSessionAgentManager(
            ContentCreationBootstrap.Infrastructure infra) {
        return infra.sessionAgentManager();
    }

    @Bean
    public ChannelManager agentscopeChannelManager(ContentCreationBootstrap.Infrastructure infra) {
        return infra.channelManager();
    }

    @Bean
    public HarnessGateway harnessGateway(ContentCreationBootstrap.Infrastructure infra) {
        return infra.harnessGateway();
    }

    @Bean
    public SessionsTool sessionsTool(ContentCreationBootstrap.Infrastructure infra) {
        return infra.sessionsTool();
    }

    /**
     * 天气工具 Bean（可选）——通过反射调用 CaiyunWeatherClient.weather()，避免 aaf-framework 硬依赖 aaf-api 的天气模块。 若
     * Spring 上下文中无 CaiyunWeatherClient Bean（天气模块未配置），返回 null，工具不注册。
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(
            name = "caiyunWeatherClient")
    public WeatherAgentTool weatherAgentTool(
            @Autowired(required = false)
                    @org.springframework.beans.factory.annotation.Qualifier("caiyunWeatherClient")
                    Object caiyunClient) {
        if (caiyunClient == null) return null;
        return new WeatherAgentTool(
                (lon, lat) -> {
                    try {
                        var method =
                                caiyunClient
                                        .getClass()
                                        .getMethod(
                                                "weather",
                                                double.class,
                                                double.class,
                                                int.class,
                                                int.class);
                        return (String) method.invoke(caiyunClient, lon, lat, 7, 24);
                    } catch (Exception e) {
                        log.warn("[WeatherBean] 调用失败: {}", e.getMessage());
                        return "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}";
                    }
                });
    }

    /**
     * 主 Agent Bean —— 内容创作。<b>prototype scope</b>：每次 {@code beanFactory.getBean(...)} 都创建新实例。
     *
     * <p>模型按六层决策链解析：用户偏好 → 系统默认 → yaml 兜底。 技能按用户 ID 加载（全局技能 + 用户私有技能），注入系统提示词。
     *
     * <p>AG-UI 端点：{@code POST /api/agui/run/content-creation}。
     */
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    @AguiAgentId("assistant")
    public Agent contentCreationAgent(
            ContentCreationProperties props,
            AafAgentServices services,
            ContentCreationBootstrap.Infrastructure infra,
            AiProperties aiProperties,
            org.springframework.beans.factory.ObjectProvider<WeatherAgentTool> weatherToolProvider,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    GenerateImageTool generateImageTool,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    GenerateVideoTool generateVideoTool,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    GenerateMusicTool generateMusicTool) {
        var resolvedModel = resolveModel(services.capabilityRouter(), AafContextHolder.userId());
        var skillLoader = new SkillContextLoader(services.jdbcTemplate());
        var skillPrompt = skillLoader.buildSkillPrompt(AafContextHolder.userId(), "COPYWRITING");
        var personaLoader = new PersonaContextLoader(services.jdbcTemplate());
        var personaPrompt = personaLoader.buildPersonaPrompt(AafContextHolder.userId());
        // 加载助理配置（system_prompt / persona / kbId / modelId）
        var assistantLoader = new AssistantContextLoader(services.jdbcTemplate());
        var assistantConfig = assistantLoader.load(AafContextHolder.assistantId());
        // 知识库自动注入：优先用助理绑定的 kbId，否则用用户级 kbId
        Long kbUserId = AafContextHolder.userId();
        var kbLoader = new KbAutoInjectLoader(services.jdbcTemplate());
        var kbContext = kbLoader.buildAutoInjectContext(kbUserId);
        var assistantPrompt = assistantConfig.buildPromptSegment();
        var weatherTool = weatherToolProvider.getIfAvailable();
        return ContentCreationAgentFactory.createMainAgent(
                props,
                services,
                infra.baseStore(),
                infra.sessionsTool(),
                resolvedModel,
                weatherTool,
                skillPrompt,
                personaPrompt,
                kbContext,
                assistantPrompt,
                generateImageTool,
                generateVideoTool,
                generateMusicTool,
                aiProperties);
    }

    /** 编辑子 Agent Bean —— prototype scope，AG-UI 端点：{@code POST /api/agui/run/editor}。 */
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    @AguiAgentId("editor")
    public Agent editorAgent(
            ContentCreationProperties props,
            AafAgentServices services,
            ContentCreationBootstrap.Infrastructure infra,
            AiProperties aiProperties) {
        var resolvedModel = resolveModel(services.capabilityRouter(), AafContextHolder.userId());
        return ContentCreationAgentFactory.createEditorAgent(
                props, services, infra.baseStore(), resolvedModel, aiProperties);
    }

    /**
     * 客服 Agent Bean —— prototype scope，未登录用户默认路由到此 Agent。
     *
     * <p>特点：
     *
     * <ul>
     *   <li>只注册 {@code search_kb} 和 {@code switch_kb} 工具，专注知识库检索回答
     *   <li>自动注入 assistantId=1（客服助理）绑定的知识库背景知识
     *   <li>系统提示词面向客服场景
     * </ul>
     *
     * <p>AG-UI 端点：{@code POST /api/agui/run/customer-service}。
     */
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    @AguiAgentId("customer-service")
    public Agent customerServiceAgent(
            ContentCreationProperties props,
            AafAgentServices services,
            ContentCreationBootstrap.Infrastructure infra,
            AiProperties aiProperties) {
        var resolvedModel = resolveModel(services.capabilityRouter(), AafContextHolder.userId());
        // 加载客服助理配置（seed 数据中 assistant id=1）
        var assistantLoader = new AssistantContextLoader(services.jdbcTemplate());
        var assistantConfig = assistantLoader.load(AafContextHolder.assistantId());
        var kbLoader = new KbAutoInjectLoader(services.jdbcTemplate());
        var kbContext = kbLoader.buildAutoInjectContext(null); // 公共知识库（owner_id IS NULL）
        return ContentCreationAgentFactory.createCustomerServiceAgent(
                props,
                services,
                infra.baseStore(),
                resolvedModel,
                assistantConfig.buildPromptSegment(),
                kbContext,
                aiProperties);
    }

    /**
     * 通过六层决策链解析 CHAT 模型。
     *
     * <p>显式指定层（第 1 层）由调用方在 {@code forwardedProps.modelId} 传入，在此作为 {@code explicitModelId} 读取——当前
     * Agent Bean 是 prototype，请求级上下文已由 {@link AafContextHolder} 注入。
     */
    private static com.xuejiai.aaf.framework.intelligent.core.model.AiModel resolveModel(
            CapabilityRouter router, Long userId) {
        try {
            // forwardedProps 中若有显式 modelId，可通过扩展 AafContextHolder 传入；暂无则走 4/5/6 层
            var ctx =
                    CapabilityRoutingContext.ofCapability(
                            userId, CapabilityRoutingContext.CAP_CHAT);
            return router.resolve(ctx);
        } catch (Exception e) {
            log.warn("[ContentCreation] 六层模型决策链解析失败，将回退到 props.modelId: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 注册 AafContextHolder 的 ThreadLocalAccessor，使 Reactor Hooks.enableAutomaticContextPropagation()
     * 能在响应式链跨线程切换时自动传播 AafContext（userId、threadId 等），解决 boundedElastic 线程丢失上下文问题。
     */
    @Bean
    public ThreadLocalAccessor<AafContextHolder.AafContext> aafContextThreadLocalAccessor() {
        return new ThreadLocalAccessor<>() {
            private static final String KEY = "aaf.context";

            @Override
            public Object key() {
                return KEY;
            }

            @Override
            public AafContextHolder.AafContext getValue() {
                return AafContextHolder.get();
            }

            @Override
            public void setValue(AafContextHolder.AafContext value) {
                AafContextHolder.set(value);
            }

            @Override
            public void reset() {
                AafContextHolder.clear();
            }
        };
    }
}
