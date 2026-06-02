package com.xuejiai.aaf.framework.intelligent.agentscope.runtime;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.framework.engine.skill.SkillStore;
import com.xuejiai.aaf.framework.engine.tool.ToolCatalogProvider;
import com.xuejiai.aaf.framework.engine.workflow.WorkflowTool;
import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinition;
import com.xuejiai.aaf.framework.intelligent.agentscope.hook.AafToolPermissionHook;
import com.xuejiai.aaf.framework.intelligent.agentscope.hook.AafToolWhitelistHook;
import com.xuejiai.aaf.framework.intelligent.agentscope.hook.AafTraceHook;
import com.xuejiai.aaf.framework.intelligent.agentscope.hook.MemoryContextHook;
import com.xuejiai.aaf.framework.intelligent.agentscope.hook.TokenMeteringHook;
import com.xuejiai.aaf.framework.intelligent.agentscope.knowledge.AafKnowledge;
import com.xuejiai.aaf.framework.intelligent.agentscope.memory.AafAutoContextMemoryAdapter;
import com.xuejiai.aaf.framework.intelligent.agentscope.tool.AgentScopeToolGovernanceService;
import com.xuejiai.aaf.framework.intelligent.agentscope.tool.McpToolService;
import com.xuejiai.aaf.framework.intelligent.core.agent.AgentExecutor;
import com.xuejiai.aaf.framework.intelligent.core.agent.AgentRuntime;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.autocontext.AutoContextConfig;
import io.agentscope.core.memory.autocontext.AutoContextHook;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.rag.RAGMode;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AgentScope 运行时——AAF 与 AgentScope 的唯一桥梁。
 *
 * <h3>职责</h3>
 *
 * <p>将 AAF 领域对象 {@link AgentDefinition}（DB 元数据）编译为 AgentScope {@link ReActAgent}（可执行实例）。 上层只依赖
 * {@link AgentRuntime} 接口，本类是该接口在 AgentScope 技术栈上的唯一实现。
 *
 * <h3>构建流程</h3>
 *
 * <pre>
 * AgentDefinition（DB 蓝图）
 *   │
 *   ├─ configureModel()     选模型（CapabilityRouter 六层决策链 → OpenAIChatModel）
 *   ├─ 配置 Memory           AutoContextMemory（按 memoryConfig 配置压缩参数）
 *   ├─ 注册 Hook（5 个）      行为扩展，不改 ReActAgent 源码
 *   │   ├─ TokenMeteringHook     Token 计量
 *   │   ├─ AafTraceHook          执行轨迹 → 发布事件 → 消息持久化
 *   │   ├─ AafToolPermissionHook 工具调用 HITL 门控
 *   │   ├─ MemoryContextHook     每轮 LLM 前注入记忆/知识库检索结果
 *   │   └─ AutoContextHook       触发 AutoContextMemory 压缩检查
 *   ├─ AafToolWhitelistHook      工具白名单过滤（per-agent 实例，非单例）
 *   ├─ buildToolkit()       注册工具（McpToolService + 治理规则）
 *   ├─ buildSkillBox()      加载技能（渐进披露，激活后才暴露工具）
 *   │
 *   └─ ReActAgent.builder().build() → 可执行的 Agent 实例
 * </pre>
 *
 * <h3>两种输出方式</h3>
 *
 * <ul>
 *   <li>{@link #create} → 包装为 {@link AgentScopeAgentAdapter}（实现 {@link AgentExecutor}），供 AgentPool
 *       使用
 *   <li>{@link #createRaw} → 返回原始 {@link ReActAgent}，供 AG-UI Registry 注册（需要 agent.stream()）
 * </ul>
 *
 * <h3>集成机制</h3>
 *
 * <p>本类通过组合（has-a）使用 AgentScope API，不继承任何 AgentScope 类。 所有 AAF 行为通过 Hook 接口注入 ReAct 循环，保持
 * ReActAgent 不被子类化。
 *
 * @see AgentScopeAgentAdapter 适配 AgentExecutor 接口
 * @see AafAutoContextMemoryAdapter 上下文压缩
 * @see MemoryContextHook 记忆/知识库检索注入
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentScopeRuntime implements AgentRuntime {

    private final TokenMeteringHook tokenMeteringHook;
    private final AafTraceHook aafTraceHook;
    private final AafToolPermissionHook aafToolPermissionHook;
    private final MemoryContextHook memoryContextHook;
    private final McpToolService mcpToolService;
    private final AiModelRepository modelRepository;
    private final CapabilityRouter capabilityRouter;
    private final ObjectProvider<ToolCatalogProvider> toolCatalogProvider;
    private final AgentScopeToolGovernanceService toolGovernanceService;
    private final SkillStore skillStore;
    private final WorkflowTool workflowTool;
    private final AafKnowledge aafKnowledge;

    /**
     * 创建 AgentExecutor（AAF 接口），供 AgentPool 借出/归还。 内部调用 {@link #buildReActAgent} 后包装为 {@link
     * AgentScopeAgentAdapter}。
     */
    @Override
    public AgentExecutor create(AgentDefinition definition, List<String> tools) {
        var agent = buildReActAgent(definition);
        return new AgentScopeAgentAdapter(agent);
    }

    /**
     * 创建原始 AgentScope Agent（供 AG-UI Registry 直接注册）。 AG-UI 协议需要调用 {@code agent.stream(msg)}
     * 产出事件流，因此返回原始类型。
     */
    public io.agentscope.core.agent.Agent createRaw(AgentDefinition definition) {
        return buildReActAgent(definition);
    }

    /**
     * 核心构建方法——将 AgentDefinition 编译为 ReActAgent。
     *
     * <p>构建顺序：模型 → Memory → Hook → Toolkit → SkillBox → build()
     */
    private ReActAgent buildReActAgent(AgentDefinition definition) {
        var builder =
                ReActAgent.builder()
                        .name(definition.getName())
                        .sysPrompt(definition.getSystemPrompt())
                        .maxIters(definition.getMaxIterations())
                        // 1.1.0 新特性：工具调用中断后自动恢复
                        .enablePendingToolRecovery(true)
                        // 1.1.0 新特性：模型调用重试/超时
                        .modelExecutionConfig(
                                io.agentscope.core.agent.ExecutionConfig.builder()
                                        .maxRetries(3)
                                        .retryDelay(java.time.Duration.ofSeconds(1))
                                        .timeout(java.time.Duration.ofSeconds(60))
                                        .build())
                        // Hook 注册顺序决定同优先级时的执行顺序，但各 Hook 已显式声明 priority()
                        .hook(tokenMeteringHook) // priority=1000，最先执行，记录 Token
                        .hook(aafTraceHook) // priority=900，采集轨迹发布事件
                        .hook(aafToolPermissionHook) // priority=50，工具调用 HITL 门控
                        .hook(memoryContextHook) // priority=800，LLM 前注入检索结果
                        .hook(new AutoContextHook()) // 配套 AutoContextMemory，触发压缩
                        .hook(
                                new AafToolWhitelistHook( // per-agent 实例（白名单不同）
                                        parseList(definition.getTools()),
                                        toolCatalogProvider.getIfAvailable()));

        // 模型 + Memory（含压缩配置）
        configureModel(builder, definition);

        // 工具集：MCP 注册 + 治理规则（速率限制、审计等）
        var toolkit = mcpToolService.buildToolkit(definition);
        toolGovernanceService.apply(toolkit, definition);
        builder.toolkit(toolkit);

        // 知识库：按 memoryConfig.ragMode 配置接入（默认不接入，由 MemoryContextHook 融合检索覆盖）
        configureKnowledge(builder, definition);

        // 技能：渐进披露，激活后才暴露绑定工具
        var skillBox = buildSkillBox(toolkit, definition.getAgentId());
        if (skillBox != null) {
            builder.skillBox(skillBox);
        }

        return builder.build();
    }

    /**
     * 构建 SkillBox：从 ai_skill_definition 表加载当前 Agent 绑定的技能。
     *
     * <p>AgentScope SkillBox 实现渐进式披露：Agent 默认只看到 skill 列表， 用户明确激活某技能后才暴露该技能绑定的工具，避免工具过多干扰 LLM 决策。
     */
    private SkillBox buildSkillBox(io.agentscope.core.tool.Toolkit toolkit, String agentId) {
        if (agentId == null) return null;
        var skills = new java.util.ArrayList<>(skillStore.findByAgentId(agentId));
        skills.addAll(skillStore.findGlobal());
        if (skills.isEmpty()) return null;

        var skillBox = new SkillBox(toolkit);
        skillBox.registerSkillLoadTool(); // 注册 load_skill 工具，让 Agent 能主动激活技能

        for (var skill : skills) {
            var agentSkill =
                    AgentSkill.builder()
                            .name(skill.skillId())
                            .description(skill.description() != null ? skill.description() : "")
                            .skillContent(skill.instructions() != null ? skill.instructions() : "")
                            .build();
            var registration = skillBox.registration().skill(agentSkill);
            if (skill.tools() != null && skill.tools().contains("start_workflow")) {
                registration.tool(workflowTool);
            }
            registration.apply();
        }
        return skillBox;
    }

    /**
     * 配置模型和 Memory。
     *
     * <p>模型选择走 {@link CapabilityRouter} 六层决策链（全局默认→编排配置→用户偏好→...）。 Memory 使用 {@link
     * AafAutoContextMemoryAdapter}（AutoContextMemory）， 按 {@code definition.memoryConfig} JSON
     * 配置压缩参数，为 null 时使用全局默认值。
     */
    private void configureModel(ReActAgent.Builder builder, AgentDefinition definition) {
        var ctx =
                new CapabilityRoutingContext(
                        null,
                        CapabilityRoutingContext.CAP_CHAT,
                        null,
                        definition.getModelId(),
                        null);
        var resolvedModelId = capabilityRouter.resolve(ctx);

        var dbModel = modelRepository.findByModelIdAndEnabledTrue(resolvedModelId).orElse(null);
        OpenAIChatModel chatModel;
        if (dbModel != null) {
            chatModel = buildFromDb(dbModel);
        } else {
            log.warn("模型 [{}] 不可用，降级使用模型名直接调用", resolvedModelId);
            chatModel = OpenAIChatModel.builder().modelName(resolvedModelId).build();
        }
        builder.model(chatModel);
        builder.memory(
                AafAutoContextMemoryAdapter.create(
                        chatModel, parseMemoryConfig(definition.getMemoryConfig())));
    }

    /**
     * 配置知识库接入。
     *
     * <p>通过 memoryConfig.ragMode 控制：
     *
     * <ul>
     *   <li>{@code "FUSION"}（默认）：不接入 AgentScope Knowledge，由 MemoryContextHook 做记忆+知识库 RRF 融合检索
     *   <li>{@code "GENERIC"}：接入 AgentScope Knowledge，每轮 LLM 前自动检索知识库注入（被动）
     *   <li>{@code "AGENTIC"}：接入 AgentScope Knowledge，暴露为 retrieve_knowledge 工具（Agent 主动查）
     *   <li>{@code "NONE"}：不做任何知识检索（MemoryContextHook 也跳过知识库部分）
     * </ul>
     */
    private void configureKnowledge(ReActAgent.Builder builder, AgentDefinition definition) {
        var json = definition.getMemoryConfig();
        if (json == null || json.isBlank()) return; // 无配置 = FUSION（默认行为，MemoryContextHook 处理）
        try {
            var node = new ObjectMapper().readTree(json);
            if (!node.has("ragMode")) return;
            var mode = node.get("ragMode").asText().toUpperCase();
            var limit = node.has("ragLimit") ? node.get("ragLimit").asInt() : 3;
            var threshold =
                    node.has("ragScoreThreshold") ? node.get("ragScoreThreshold").asDouble() : 0.3;
            var retrieveConfig =
                    RetrieveConfig.builder().limit(limit).scoreThreshold(threshold).build();
            switch (mode) {
                case "FUSION" -> {} // 默认行为，MemoryContextHook 统一处理，不接入 AgentScope Knowledge
                case "NONE" -> {} // 不做任何知识检索（MemoryContextHook 通过 knowledgeBaseId=null 自动跳过）
                case "GENERIC" ->
                        builder.knowledge(aafKnowledge)
                                .ragMode(RAGMode.GENERIC)
                                .retrieveConfig(retrieveConfig);
                case "AGENTIC" ->
                        builder.knowledge(aafKnowledge)
                                .ragMode(RAGMode.AGENTIC)
                                .retrieveConfig(retrieveConfig);
                default -> log.debug("未知 ragMode: {}，使用默认 FUSION 模式", mode);
            }
        } catch (Exception e) {
            log.warn("解析 memoryConfig.ragMode 失败: {}", e.getMessage());
        }
    }

    /** 解析 memoryConfig JSON，覆盖默认压缩参数。 为 null 或解析失败时返回全局默认配置。 */
    private AutoContextConfig parseMemoryConfig(String json) {
        var b =
                AutoContextConfig.builder()
                        .maxToken(100_000)
                        .msgThreshold(50)
                        .lastKeep(10)
                        .largePayloadThreshold(2000)
                        .minConsecutiveToolMessages(6);
        if (json != null && !json.isBlank()) {
            try {
                var node = new ObjectMapper().readTree(json);
                if (node.has("maxToken")) b.maxToken(node.get("maxToken").asInt());
                if (node.has("msgThreshold")) b.msgThreshold(node.get("msgThreshold").asInt());
                if (node.has("lastKeep")) b.lastKeep(node.get("lastKeep").asInt());
                if (node.has("largePayloadThreshold"))
                    b.largePayloadThreshold(node.get("largePayloadThreshold").asInt());
                if (node.has("minConsecutiveToolMessages"))
                    b.minConsecutiveToolMessages(node.get("minConsecutiveToolMessages").asInt());
            } catch (Exception e) {
                log.warn("解析 memoryConfig 失败，使用默认值: {}", e.getMessage());
            }
        }
        return b.build();
    }

    /** 解析 JSON 数组字符串为 List（如 ["a","b"] → List.of("a","b")）。 */
    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return List.of(json.replaceAll("[\\[\\]\"\\ ]", "").split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    /** 从 DB 模型配置构建 OpenAIChatModel（OpenAI 兼容协议，覆盖大多数模型商）。 */
    private OpenAIChatModel buildFromDb(AiModel model) {
        return OpenAIChatModel.builder()
                .modelName(model.getModelName())
                .apiKey(model.getApiKey() != null ? model.getApiKey() : "")
                .baseUrl(model.getBaseUrl())
                .build();
    }
}
