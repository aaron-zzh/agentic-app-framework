package com.xuejiai.aaf.framework.intelligent.agent.agentscope;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.tool.ToolCatalogProvider;
import com.xuejiai.aaf.framework.engine.skill.SkillStore;
import com.xuejiai.aaf.framework.engine.workflow.WorkflowTool;
import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinition;
import com.xuejiai.aaf.framework.intelligent.agent.AgentRuntime;
import com.xuejiai.aaf.framework.intelligent.agent.McpToolService;
import com.xuejiai.aaf.framework.intelligent.core.agent.AgentExecutor;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.intelligent.core.token.TokenMeteringHook;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.autocontext.AutoContextHook;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AgentScope 运行时——所有 AgentScope 依赖收敛于此。
 *
 * <p>上层（AgentFactory）只依赖 {@link AgentRuntime} 接口， 本类负责将 AAF 的 AgentDefinition 转为 AgentScope
 * ReActAgent。
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

    @Override
    public AgentExecutor create(AgentDefinition definition, List<String> tools) {
        var agent = buildReActAgent(definition);
        return new AgentScopeAgentAdapter(agent);
    }

    /** 创建原始 AgentScope Agent（供 AG-UI Registry 直接注册）。 */
    public io.agentscope.core.agent.Agent createRaw(AgentDefinition definition) {
        return buildReActAgent(definition);
    }

    private ReActAgent buildReActAgent(AgentDefinition definition) {
        var builder =
                ReActAgent.builder()
                        .name(definition.getName())
                        .sysPrompt(definition.getSystemPrompt())
                        .hook(tokenMeteringHook)
                        .hook(aafTraceHook)
                        .hook(aafToolPermissionHook)
                        .hook(memoryContextHook)
                        .hook(new AutoContextHook())
                        .hook(new AafToolWhitelistHook(
                                parseList(definition.getTools()), toolCatalogProvider.getIfAvailable()));

        configureModel(builder, definition);
        var toolkit = mcpToolService.buildToolkit(definition);
        toolGovernanceService.apply(toolkit, definition);
        builder.toolkit(toolkit);

        // 集成 SkillBox：从 ai_skill_definition 加载当前 Agent 关联的技能
        var skillBox = buildSkillBox(toolkit, definition.getAgentId());
        if (skillBox != null) {
            builder.skillBox(skillBox);
        }

        return builder.build();
    }

    /**
     * 构建 SkillBox：加载 ai_skill_definition 中绑定了当前 Agent 的技能，
     * 每个技能激活后才暴露绑定的工具，实现渐进式披露。
     */
    private SkillBox buildSkillBox(io.agentscope.core.tool.Toolkit toolkit, String agentId) {
        if (agentId == null) return null;
        var skills = new java.util.ArrayList<>(skillStore.findByAgentId(agentId));
        skills.addAll(skillStore.findGlobal());  // 合并全局技能
        if (skills.isEmpty()) return null;

        var skillBox = new SkillBox(toolkit);
        skillBox.registerSkillLoadTool();

        for (var skill : skills) {
            var agentSkill = AgentSkill.builder()
                    .name(skill.skillId())
                    .description(skill.description() != null ? skill.description() : "")
                    .skillContent(skill.instructions() != null ? skill.instructions() : "")
                    .build();
            var registration = skillBox.registration().skill(agentSkill);
            // 绑定了 start_workflow 的技能，激活后才暴露 WorkflowTool
            if (skill.tools() != null && skill.tools().contains("start_workflow")) {
                registration.tool(workflowTool);
            }
            registration.apply();
        }
        return skillBox;
    }

    private void configureModel(ReActAgent.Builder builder, AgentDefinition definition) {
        // 走六层决策链：definition.modelId 作为编排引擎配置（第2层），userId=null 跳过用户偏好层
        var ctx = new CapabilityRoutingContext(
                null, CapabilityRoutingContext.CAP_CHAT,
                null, definition.getModelId(), null);
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
        builder.memory(AafAutoContextMemoryAdapter.create(chatModel));
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return List.of(json.replaceAll("[\\[\\]\"\\ ]", "").split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private OpenAIChatModel buildFromDb(AiModel model) {
        return OpenAIChatModel.builder()
                .modelName(model.getModelName())
                .apiKey(model.getApiKey() != null ? model.getApiKey() : "")
                .baseUrl(model.getBaseUrl())
                .build();
    }
}
