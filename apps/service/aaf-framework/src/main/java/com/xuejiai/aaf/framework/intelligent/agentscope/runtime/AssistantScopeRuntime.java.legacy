package com.xuejiai.aaf.framework.intelligent.agentscope.runtime;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import com.xuejiai.aaf.framework.engine.skill.SkillStore;
import com.xuejiai.aaf.framework.engine.tool.ToolCatalogProvider;
import com.xuejiai.aaf.framework.intelligent.agent.AgentRegistryService;
import com.xuejiai.aaf.framework.intelligent.agentscope.hook.AafToolPermissionHook;
import com.xuejiai.aaf.framework.intelligent.agentscope.hook.AafToolWhitelistHook;
import com.xuejiai.aaf.framework.intelligent.agentscope.hook.AafTraceHook;
import com.xuejiai.aaf.framework.intelligent.agentscope.hook.MemoryContextHook;
import com.xuejiai.aaf.framework.intelligent.agentscope.hook.TokenMeteringHook;
import com.xuejiai.aaf.framework.intelligent.agentscope.knowledge.AafKnowledge;
import com.xuejiai.aaf.framework.intelligent.agentscope.memory.AafAutoContextMemoryAdapter;
import com.xuejiai.aaf.framework.intelligent.agentscope.tool.AgentScopeToolGovernanceService;
import com.xuejiai.aaf.framework.intelligent.agentscope.tool.McpToolService;
import com.xuejiai.aaf.framework.intelligent.assistant.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.AssistantDefinitionRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.persona.Persona;
import com.xuejiai.aaf.framework.intelligent.assistant.persona.PersonaRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiRoleRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.role.Role;
import com.xuejiai.aaf.framework.intelligent.core.assistant.AssistantRuntime;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.autocontext.AutoContextHook;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AssistantRuntime 的 AgentScope 实现——核心物化方法。
 *
 * <h3>职责</h3>
 *
 * <p>将 AssistantDefinition（Persona + Role + MemoryStrategy）编译为一个完整的协调者 ReActAgent。 AG-UI 入口和
 * AssistantService 入口共享此方法，确保逻辑统一。
 *
 * <h3>物化流程（主方法 {@link #materialize}）</h3>
 *
 * <pre>
 * AssistantDefinition
 *   ├─ Persona → systemPrompt（人格/角色扮演）
 *   ├─ Role → toolWhitelist + skillIds
 *   ├─ MemoryStrategy → 记忆配置
 *   ├─ knowledgeBaseId → 知识库绑定（运行时通过 AgentRunContextHolder 传递）
 *   └─ permissionScope → 工具权限边界
 *        ↓
 *   ReActAgent（协调者）
 *     ├─ sysPrompt = Persona.systemPrompt + Persona.persona
 *     ├─ model = CapabilityRouter 选模型
 *     ├─ memory = AutoContextMemory（压缩配置）
 *     ├─ toolkit = Role.toolWhitelist 过滤后的工具集
 *     ├─ skillBox = Role.skillIds 关联的技能（渐进披露）
 *     └─ hooks = 完整 Hook 链
 * </pre>
 *
 * <h3>异步事件（由 Hook 在执行期间发布）</h3>
 *
 * <ul>
 *   <li>{@code PreCallEvent} → {@code AafTraceHook} → 发布 {@code UserMessageEvent} → {@code
 *       ChatPersistenceListener} 异步写用户消息到 DB
 *   <li>{@code PostCallEvent} → {@code AafTraceHook} → 发布 {@code ExecutionCompletedEvent} → {@code
 *       ChatPersistenceListener} 异步写 AI 回复 → {@code LearningFeedbackService} 异步更新统计 → {@code
 *       MemoryWriteBackListener} 异步抽取写长期记忆
 *   <li>{@code PostReasoningEvent} → {@code AafToolPermissionHook} → 需确认时 stopAgent() → AG-UI 推送
 *       requires-action 状态 → 前端弹确认 → /confirm 恢复
 *   <li>{@code PreReasoningEvent} → {@code MemoryContextHook} → 检索记忆+知识库注入（同步，非事件）
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssistantScopeRuntime implements AssistantRuntime {

    private final AssistantDefinitionRepository assistantRepo;
    private final PersonaRepository personaRepo;
    private final AiRoleRepository roleRepo;
    private final AgentRegistryService agentRegistry;
    private final AiModelRepository modelRepository;
    private final CapabilityRouter capabilityRouter;
    private final ObjectProvider<SkillStore> skillStoreProvider;
    private final McpToolService mcpToolService;
    private final AgentScopeToolGovernanceService toolGovernanceService;
    private final ObjectProvider<ToolCatalogProvider> toolCatalogProvider;
    private final AafKnowledge aafKnowledge;

    // Hook 单例（Spring Bean）
    private final TokenMeteringHook tokenMeteringHook;
    private final AafTraceHook aafTraceHook;
    private final AafToolPermissionHook aafToolPermissionHook;
    private final MemoryContextHook memoryContextHook;

    /**
     * key = "assistantId:configHash"，configHash 由 assistant+Persona+role 的 updateTime 拼接而成。 任一实体更新
     * → updateTime 变化 → key 变化 → 自动 miss，旧实例由 GC 回收。 maximumSize 防止极端场景内存膨胀；expireAfterAccess
     * 兜底长期不访问的 key。
     */
    private final Cache<String, ReActAgent> agentCache =
            Caffeine.newBuilder().maximumSize(500).expireAfterAccess(30, TimeUnit.MINUTES).build();

    /**
     * 核心物化方法——将 AssistantDefinition 编译为协调者 ReActAgent。
     *
     * <p>执行步骤：
     *
     * <ol>
     *   <li>加载 AssistantDefinition → Persona + Role
     *   <li>构建 systemPrompt（Persona 人格 + persona）
     *   <li>选模型（CapabilityRouter 六层决策链）
     *   <li>配置 Memory（AutoContextMemory + 压缩参数）
     *   <li>注册 Hook 链（追踪、权限、记忆注入、压缩、白名单）
     *   <li>构建 Toolkit（工具集，受 Role.toolWhitelist 过滤）
     *   <li>构建 SkillBox（Role.skillIds 关联技能，渐进披露）
     *   <li>build() 返回完整协调者
     * </ol>
     */
    @Override
    public ReActAgent materialize(MaterializeContext ctx) {
        // ── Step 1: 加载配置 ──────────────────────────────────────────────────
        var assistant = assistantRepo.findById(parseLong(ctx.assistantId())).orElse(null);
        if (assistant == null) {
            log.warn("Assistant [{}] 不存在，使用默认 Agent 降级", ctx.assistantId());
            return buildFallbackAgent();
        }

        var Persona = personaRepo.findById(assistant.getPersonaId()).orElse(null);
        var role = roleRepo.findById(assistant.getDefaultRoleId()).orElse(null);

        // ── 缓存检查：key = assistantId:configHash ────────────────────────────
        // configHash 由 assistant/Persona/role 的 updateTime 拼接，任一变更 → key 变化 → 自动 miss
        var cacheKey = buildCacheKey(ctx.assistantId(), assistant, Persona, role);
        var cached = agentCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("命中助理缓存: assistantId={}", ctx.assistantId());
            return cached;
        }

        // ── Step 2: 构建 systemPrompt ─────────────────────────────────────────
        // Persona.systemPrompt 定义核心人格，Persona.persona 补充风格细节
        var sysPrompt = buildSystemPrompt(Persona);

        // ── Step 3: 选模型 ────────────────────────────────────────────────────
        // CapabilityRouter 六层决策链：全局默认→编排配置→用户偏好→...
        var chatModel = resolveModel(null); // TODO: 从 assistant/role 读 modelId 配置

        // ── Step 4: 配置 Memory ───────────────────────────────────────────────
        // AutoContextMemory：对话历史存储 + Token 超限自动压缩
        // 与 MemoryContextHook（检索注入）独立——前者管存储，后者管检索注入
        var memory = AafAutoContextMemoryAdapter.create(chatModel);

        // ── Step 5: 注册 Hook 链 ──────────────────────────────────────────────
        var toolWhitelist = parseList(role != null ? role.getToolWhitelist() : null);
        var builder =
                ReActAgent.builder()
                        .name(Persona != null ? Persona.getName() : "Assistant")
                        .sysPrompt(sysPrompt)
                        .maxIters(10)
                        .model(chatModel)
                        .memory(memory)
                        // 1.1.0 新特性：工具调用中断后自动恢复（HITL stopAgent 恢复更健壮）
                        .enablePendingToolRecovery(true)
                        // 1.1.0 新特性：模型调用重试/超时（网络抖动时自动重试，避免单次失败中断对话）
                        .modelExecutionConfig(
                                io.agentscope.core.model.ExecutionConfig.builder()
                                        .maxAttempts(3)
                                        .initialBackoff(java.time.Duration.ofSeconds(1))
                                        .timeout(java.time.Duration.ofSeconds(60))
                                        .build())
                        // 1.1.0 新特性：工具执行上下文——工具方法可声明 AgentRunContext 参数自动注入，无需读 ThreadLocal
                        .toolExecutionContext(
                                io.agentscope.core.tool.ToolExecutionContext.builder()
                                        .register(
                                                new com.xuejiai.aaf.framework.intelligent.agent
                                                        .context.AgentRunContext(
                                                        ctx.threadId(),
                                                        ctx.userId(),
                                                        "assistant",
                                                        ctx.assistantId(),
                                                        ctx.threadId(),
                                                        ctx.knowledgeBaseId()))
                                        .build())
                        // --- Hook 链（按 priority 排序执行）---
                        // priority=1000: Token 计量（最先执行，记录输入输出 Token）
                        .hook(tokenMeteringHook)
                        // priority=900: 执行轨迹（PreCallEvent→发布 UserMessageEvent，PostCallEvent→发布
                        // ExecutionCompletedEvent）
                        //   → 异步事件：ChatPersistenceListener 写 DB、LearningFeedbackService
                        // 更新统计、MemoryWriteBackListener 写长期记忆
                        .hook(aafTraceHook)
                        // priority=800: 记忆/知识库检索注入（PreReasoningEvent→UnifiedRetrievalService→临时
                        // system message）
                        .hook(memoryContextHook)
                        // priority=50: 工具权限 HITL 门控（PostReasoningEvent→需确认时 stopAgent()）
                        //   → 异步事件：AG-UI 推送 requires-action → 前端/渠道确认 → /confirm 恢复
                        .hook(aafToolPermissionHook)
                        // AutoContextMemory 配套：每轮前检查 Token 是否超限，触发压缩
                        .hook(new AutoContextHook())
                        // 工具白名单（per-assistant 实例）：Role.toolWhitelist 定义允许的工具
                        .hook(
                                new AafToolWhitelistHook(
                                        toolWhitelist, toolCatalogProvider.getIfAvailable()));

        // ── Step 6: 构建 Toolkit ──────────────────────────────────────────────
        // 使用默认 Agent 定义的工具集（后续改为按 Role 配置）
        var defaultAgent = agentRegistry.listActive().stream().findFirst().orElse(null);
        if (defaultAgent != null) {
            var toolkit = mcpToolService.buildToolkit(defaultAgent);
            toolGovernanceService.apply(toolkit, defaultAgent);
            builder.toolkit(toolkit);

            // ── Step 7: 构建 SkillBox ──────────────────────────────────────────
            // Role.skillIds 关联的技能，激活后才暴露绑定工具（渐进披露）
            var skillBox = buildSkillBox(toolkit, role);
            if (skillBox != null) {
                builder.skillBox(skillBox);
            }
        }

        // ── Step 8: 启用 PlanNotebook ─────────────────────────────────────────
        // L2 多步规划：LLM 自主判断是否需要创建计划（complex task 时自动触发，简单任务不触发）
        builder.enablePlan();

        var agent = builder.build();
        log.debug(
                "物化助理 [{}]: Persona={}, role={}, userId={}",
                ctx.assistantId(),
                Persona != null ? Persona.getId() : "null",
                role != null ? role.getId() : "null",
                ctx.userId());
        agentCache.put(cacheKey, agent);
        return agent;
    }

    /** 构建 systemPrompt：Persona 人格 + persona 风格 */
    private String buildSystemPrompt(Persona persona) {
        if (persona == null) return "你是一个有帮助的 AI 助手。";
        var sb = new StringBuilder();
        if (persona.getSystemPrompt() != null) {
            sb.append(persona.getSystemPrompt());
        }
        if (persona.getPersona() != null && !persona.getPersona().isBlank()) {
            sb.append("\n\n## 人格风格\n").append(persona.getPersona());
        }
        return sb.isEmpty() ? "你是一个有帮助的 AI 助手。" : sb.toString();
    }

    /** 选模型——走 CapabilityRouter 六层决策链 */
    private OpenAIChatModel resolveModel(String modelId) {
        var ctx =
                new CapabilityRoutingContext(
                        null, CapabilityRoutingContext.CAP_CHAT, null, modelId, null);
        var resolvedModel = capabilityRouter.resolve(ctx);
        return OpenAIChatModel.builder()
                .modelName(resolvedModel.getModelName())
                .apiKey(
                        resolvedModel.effectiveApiKey() != null
                                ? resolvedModel.effectiveApiKey()
                                : "")
                .baseUrl(resolvedModel.effectiveBaseUrl())
                .build();
    }

    /** 构建 SkillBox：从 Role.skillIds 加载技能 */
    private SkillBox buildSkillBox(Toolkit toolkit, Role role) {
        if (role == null || role.getSkillIds() == null || role.getSkillIds().isBlank()) {
            return null;
        }
        var skillIds = parseLongList(role.getSkillIds());
        if (skillIds.isEmpty()) return null;

        var skillBox = new SkillBox(toolkit);
        skillBox.registerSkillLoadTool();

        var skillStore = skillStoreProvider.getIfAvailable();
        if (skillStore == null) return skillBox;

        for (var skillId : skillIds) {
            var skillOpt = skillStore.findBySkillId(skillId);
            if (skillOpt.isEmpty()) continue;
            var skill = skillOpt.get();
            var agentSkill =
                    AgentSkill.builder()
                            .name(skill.skillId() != null ? skill.skillId().toString() : "")
                            .description(skill.description() != null ? skill.description() : "")
                            .skillContent(skill.instructions() != null ? skill.instructions() : "")
                            .build();
            var registration = skillBox.registration().skill(agentSkill);
            // 技能不再绑定工具：工具由角色级 tool_whitelist ∩ Agent 级 allowed_tools 两级收窄统一治理
            registration.apply();
        }
        return skillBox;
    }

    /** 降级：Assistant 不存在时构建最小可用 Agent */
    private ReActAgent buildFallbackAgent() {
        var model = resolveModel(null);
        return ReActAgent.builder()
                .name("DefaultAssistant")
                .sysPrompt("你是一个有帮助的 AI 助手。")
                .model(model)
                .memory(AafAutoContextMemoryAdapter.create(model))
                .maxIters(10)
                .hook(tokenMeteringHook)
                .hook(aafTraceHook)
                .hook(memoryContextHook)
                .hook(new AutoContextHook())
                .build();
    }

    /**
     * 构建缓存 key：assistantId + ":" + configHash。 configHash 由 assistant/Persona/role 的 updateTime
     * 拼接，任一变更 → key 不同 → 旧缓存自动失效。
     */
    private String buildCacheKey(
            String assistantId, AssistantDefinition assistant, Persona persona, Role role) {
        String hash =
                ts(assistant.getUpdateTime())
                        + ":"
                        + ts(persona != null ? persona.getUpdateTime() : null)
                        + ":"
                        + ts(role != null ? role.getUpdateTime() : null);
        return assistantId + ":" + hash;
    }

    private String ts(LocalDateTime t) {
        return t != null ? String.valueOf(t.hashCode()) : "0";
    }

    /** 将字符串 ID 解析为 Long（解析失败返回 -1，Repository 查不到会返回 empty） */
    private Long parseLong(String id) {
        if (id == null || id.isBlank()) return -1L;
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) return List.of();
        return List.of(json.replaceAll("[\\[\\]\"\\ ]", "").split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private List<Long> parseLongList(String json) {
        if (json == null || json.isBlank()) return List.of();
        return List.of(json.replaceAll("[\\[\\]\"\\ ]", "").split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(
                        s -> {
                            try {
                                return Long.parseLong(s);
                            } catch (NumberFormatException e) {
                                return null;
                            }
                        })
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
