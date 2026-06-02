package com.xuejiai.aaf.framework.intelligent.agentscope.runtime;

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
import com.xuejiai.aaf.framework.intelligent.assistant.actor.Actor;
import com.xuejiai.aaf.framework.intelligent.assistant.actor.ActorRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiRoleRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.role.Role;
import com.xuejiai.aaf.framework.intelligent.core.assistant.AssistantRuntime;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinition;
import com.xuejiai.aaf.framework.intelligent.agent.AgentRegistryService;
import com.xuejiai.aaf.framework.engine.skill.SkillStore;
import com.xuejiai.aaf.framework.engine.tool.ToolCatalogProvider;
import com.xuejiai.aaf.framework.engine.workflow.WorkflowTool;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.autocontext.AutoContextConfig;
import io.agentscope.core.memory.autocontext.AutoContextHook;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AssistantRuntime 的 AgentScope 实现——核心物化方法。
 *
 * <h3>职责</h3>
 * <p>将 AssistantDefinition（Actor + Role + MemoryStrategy）编译为一个完整的协调者 ReActAgent。
 * AG-UI 入口和 AssistantService 入口共享此方法，确保逻辑统一。
 *
 * <h3>物化流程（主方法 {@link #materialize}）</h3>
 * <pre>
 * AssistantDefinition
 *   ├─ Actor → systemPrompt（人格/角色扮演）
 *   ├─ Role → toolWhitelist + skillIds
 *   ├─ MemoryStrategy → 记忆配置
 *   ├─ knowledgeBaseId → 知识库绑定（运行时通过 AgentRunContextHolder 传递）
 *   └─ permissionScope → 工具权限边界
 *        ↓
 *   ReActAgent（协调者）
 *     ├─ sysPrompt = Actor.systemPrompt + Actor.persona
 *     ├─ model = CapabilityRouter 选模型
 *     ├─ memory = AutoContextMemory（压缩配置）
 *     ├─ toolkit = Role.toolWhitelist 过滤后的工具集
 *     ├─ skillBox = Role.skillIds 关联的技能（渐进披露）
 *     └─ hooks = 完整 Hook 链
 * </pre>
 *
 * <h3>异步事件（由 Hook 在执行期间发布）</h3>
 * <ul>
 *   <li>{@code PreCallEvent} → {@code AafTraceHook} → 发布 {@code UserMessageEvent}
 *       → {@code ChatPersistenceListener} 异步写用户消息到 DB</li>
 *   <li>{@code PostCallEvent} → {@code AafTraceHook} → 发布 {@code ExecutionCompletedEvent}
 *       → {@code ChatPersistenceListener} 异步写 AI 回复
 *       → {@code LearningFeedbackService} 异步更新统计
 *       → {@code MemoryWriteBackListener} 异步抽取写长期记忆</li>
 *   <li>{@code PostReasoningEvent} → {@code AafToolPermissionHook} → 需确认时 stopAgent()
 *       → AG-UI 推送 requires-action 状态 → 前端弹确认 → /confirm 恢复</li>
 *   <li>{@code PreReasoningEvent} → {@code MemoryContextHook} → 检索记忆+知识库注入（同步，非事件）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssistantScopeRuntime implements AssistantRuntime {

    private final AssistantDefinitionRepository assistantRepo;
    private final ActorRepository actorRepo;
    private final AiRoleRepository roleRepo;
    private final AgentRegistryService agentRegistry;
    private final AiModelRepository modelRepository;
    private final CapabilityRouter capabilityRouter;
    private final SkillStore skillStore;
    private final McpToolService mcpToolService;
    private final AgentScopeToolGovernanceService toolGovernanceService;
    private final WorkflowTool workflowTool;
    private final ObjectProvider<ToolCatalogProvider> toolCatalogProvider;
    private final AafKnowledge aafKnowledge;

    // Hook 单例（Spring Bean）
    private final TokenMeteringHook tokenMeteringHook;
    private final AafTraceHook aafTraceHook;
    private final AafToolPermissionHook aafToolPermissionHook;
    private final MemoryContextHook memoryContextHook;

    /**
     * 核心物化方法——将 AssistantDefinition 编译为协调者 ReActAgent。
     *
     * <p>执行步骤：
     * <ol>
     *   <li>加载 AssistantDefinition → Actor + Role</li>
     *   <li>构建 systemPrompt（Actor 人格 + persona）</li>
     *   <li>选模型（CapabilityRouter 六层决策链）</li>
     *   <li>配置 Memory（AutoContextMemory + 压缩参数）</li>
     *   <li>注册 Hook 链（追踪、权限、记忆注入、压缩、白名单）</li>
     *   <li>构建 Toolkit（工具集，受 Role.toolWhitelist 过滤）</li>
     *   <li>构建 SkillBox（Role.skillIds 关联技能，渐进披露）</li>
     *   <li>build() 返回完整协调者</li>
     * </ol>
     */
    @Override
    public ReActAgent materialize(MaterializeContext ctx) {
        // ── Step 1: 加载配置 ──────────────────────────────────────────────────
        var assistant = assistantRepo.findByAssistantId(ctx.assistantId()).orElse(null);
        if (assistant == null) {
            log.warn("Assistant [{}] 不存在，使用默认 Agent 降级", ctx.assistantId());
            return buildFallbackAgent();
        }

        var actor = actorRepo.findByActorId(assistant.getActorId()).orElse(null);
        var role = roleRepo.findByRoleId(assistant.getRoleId()).orElse(null);

        // ── Step 2: 构建 systemPrompt ─────────────────────────────────────────
        // Actor.systemPrompt 定义核心人格，Actor.persona 补充风格细节
        var sysPrompt = buildSystemPrompt(actor);

        // ── Step 3: 选模型 ────────────────────────────────────────────────────
        // CapabilityRouter 六层决策链：全局默认→编排配置→用户偏好→...
        var chatModel = resolveModel(null); // TODO: 从 assistant/role 读 modelId 配置

        // ── Step 4: 配置 Memory ───────────────────────────────────────────────
        // AutoContextMemory：对话历史存储 + Token 超限自动压缩
        // 与 MemoryContextHook（检索注入）独立——前者管存储，后者管检索注入
        var memory = AafAutoContextMemoryAdapter.create(chatModel);

        // ── Step 5: 注册 Hook 链 ──────────────────────────────────────────────
        var toolWhitelist = parseList(role != null ? role.getToolWhitelist() : null);
        var builder = ReActAgent.builder()
                .name(actor != null ? actor.getName() : "Assistant")
                .sysPrompt(sysPrompt)
                .maxIters(10)
                .model(chatModel)
                .memory(memory)
                // 1.1.0 新特性：工具调用中断后自动恢复（HITL stopAgent 恢复更健壮）
                .enablePendingToolRecovery(true)
                // --- Hook 链（按 priority 排序执行）---
                // priority=1000: Token 计量（最先执行，记录输入输出 Token）
                .hook(tokenMeteringHook)
                // priority=900: 执行轨迹（PreCallEvent→发布 UserMessageEvent，PostCallEvent→发布 ExecutionCompletedEvent）
                //   → 异步事件：ChatPersistenceListener 写 DB、LearningFeedbackService 更新统计、MemoryWriteBackListener 写长期记忆
                .hook(aafTraceHook)
                // priority=800: 记忆/知识库检索注入（PreReasoningEvent→UnifiedRetrievalService→临时 system message）
                .hook(memoryContextHook)
                // priority=50: 工具权限 HITL 门控（PostReasoningEvent→需确认时 stopAgent()）
                //   → 异步事件：AG-UI 推送 requires-action → 前端/渠道确认 → /confirm 恢复
                .hook(aafToolPermissionHook)
                // AutoContextMemory 配套：每轮前检查 Token 是否超限，触发压缩
                .hook(new AutoContextHook())
                // 工具白名单（per-assistant 实例）：Role.toolWhitelist 定义允许的工具
                .hook(new AafToolWhitelistHook(toolWhitelist, toolCatalogProvider.getIfAvailable()));

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

        var agent = builder.build();
        log.debug("物化助理 [{}]: actor={}, role={}, userId={}",
                ctx.assistantId(),
                actor != null ? actor.getActorId() : "null",
                role != null ? role.getRoleId() : "null",
                ctx.userId());
        return agent;
    }

    /** 构建 systemPrompt：Actor 人格 + persona 风格 */
    private String buildSystemPrompt(Actor actor) {
        if (actor == null) return "你是一个有帮助的 AI 助手。";
        var sb = new StringBuilder();
        if (actor.getSystemPrompt() != null) {
            sb.append(actor.getSystemPrompt());
        }
        if (actor.getPersona() != null && !actor.getPersona().isBlank()) {
            sb.append("\n\n## 人格风格\n").append(actor.getPersona());
        }
        return sb.isEmpty() ? "你是一个有帮助的 AI 助手。" : sb.toString();
    }

    /** 选模型——走 CapabilityRouter 六层决策链 */
    private OpenAIChatModel resolveModel(String modelId) {
        var ctx = new CapabilityRoutingContext(
                null, CapabilityRoutingContext.CAP_CHAT, null, modelId, null);
        var resolvedModelId = capabilityRouter.resolve(ctx);
        var dbModel = modelRepository.findByModelIdAndEnabledTrue(resolvedModelId).orElse(null);
        if (dbModel != null) {
            return OpenAIChatModel.builder()
                    .modelName(dbModel.getModelName())
                    .apiKey(dbModel.getApiKey() != null ? dbModel.getApiKey() : "")
                    .baseUrl(dbModel.getBaseUrl())
                    .build();
        }
        log.warn("模型 [{}] 不可用，降级直接调用", resolvedModelId);
        return OpenAIChatModel.builder().modelName(resolvedModelId).build();
    }

    /** 构建 SkillBox：从 Role.skillIds 加载技能 */
    private SkillBox buildSkillBox(Toolkit toolkit, Role role) {
        if (role == null || role.getSkillIds() == null || role.getSkillIds().isBlank()) {
            return null;
        }
        var skillIds = parseList(role.getSkillIds());
        if (skillIds.isEmpty()) return null;

        var skillBox = new SkillBox(toolkit);
        skillBox.registerSkillLoadTool();

        for (var skillId : skillIds) {
            var skillOpt = skillStore.findBySkillId(skillId);
            if (skillOpt.isEmpty()) continue;
            var skill = skillOpt.get();
            var agentSkill = AgentSkill.builder()
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

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) return List.of();
        return List.of(json.replaceAll("[\\[\\]\"\\ ]", "").split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
