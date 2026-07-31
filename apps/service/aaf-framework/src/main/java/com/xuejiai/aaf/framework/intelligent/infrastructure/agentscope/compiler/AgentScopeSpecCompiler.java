package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.compiler;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.model.SubagentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;
import com.xuejiai.aaf.framework.intelligent.assistant.application.EffectiveToolResolver;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelSpec;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.model.AgentScopeModelResolver;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.tool.AgentScopeToolkitFactory;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AgentId;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;

/** 将版本化 AAF AgentSpec 编译并缓存为无状态 HarnessAgent。 */
public final class AgentScopeSpecCompiler implements AutoCloseable {

    private final AgentStateStore stateStore;
    private final AgentScopeToolkitFactory toolkitFactory;
    private final AgentScopeModelResolver modelResolver;
    private final EffectiveToolResolver effectiveToolResolver;
    private final ConcurrentMap<DefinitionKey, HarnessAgent> cache = new ConcurrentHashMap<>();

    public AgentScopeSpecCompiler(
            AgentStateStore stateStore,
            AgentScopeToolkitFactory toolkitFactory,
            AgentScopeModelResolver modelResolver,
            EffectiveToolResolver effectiveToolResolver) {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore 不能为空");
        this.toolkitFactory = Objects.requireNonNull(toolkitFactory, "toolkitFactory 不能为空");
        this.modelResolver = Objects.requireNonNull(modelResolver, "modelResolver 不能为空");
        this.effectiveToolResolver =
                Objects.requireNonNull(effectiveToolResolver, "effectiveToolResolver 不能为空");
    }

    /** 按完整不可变执行画像命中预定义 Agent 编译产物。 */
    public HarnessAgent compile(
            AgentSpec spec, String skillSystemPromptAppendix, Set<String> roleAllowedToolNames) {
        Objects.requireNonNull(spec, "spec 不能为空");
        Objects.requireNonNull(skillSystemPromptAppendix, "skillSystemPromptAppendix 不能为空");
        Objects.requireNonNull(roleAllowedToolNames, "roleAllowedToolNames 不能为空");
        var effectiveTools =
                List.copyOf(effectiveToolResolver.resolve(roleAllowedToolNames, spec.tools()));
        var effectiveSystemPrompt = appendPrompt(spec.systemPrompt(), skillSystemPromptAppendix);
        var key =
                new DefinitionKey(
                        spec.agentId(), spec.version(), effectiveTools, effectiveSystemPrompt);
        return cache.computeIfAbsent(
                key, ignored -> compileNew(spec, effectiveTools, effectiveSystemPrompt));
    }

    /** 现场编译动态子智能体；规格没有稳定版本键，因此不进入定义缓存。 */
    public HarnessAgent compileDynamic(
            SubagentSpec.Dynamic spec,
            ModelSpec executionModel,
            String skillSystemPromptAppendix,
            Set<String> roleAllowedToolNames) {
        Objects.requireNonNull(spec, "spec 不能为空");
        Objects.requireNonNull(executionModel, "executionModel 不能为空");
        Objects.requireNonNull(skillSystemPromptAppendix, "skillSystemPromptAppendix 不能为空");
        Objects.requireNonNull(roleAllowedToolNames, "roleAllowedToolNames 不能为空");
        if (spec.inheritParentTools()) {
            throw new IllegalArgumentException("Dynamic 子智能体暂不支持继承父 Agent 工具");
        }
        var effectiveTools =
                List.copyOf(effectiveToolResolver.resolve(roleAllowedToolNames, spec.tools()));
        var effectiveSystemPrompt =
                appendPrompt(spec.systemPromptFragment(), skillSystemPromptAppendix);
        var toolkit = toolkitFactory.create(effectiveTools);
        var agent =
                HarnessAgent.builder()
                        .agentId(spec.identifier())
                        .name(spec.name())
                        .description(spec.description())
                        .sysPrompt(effectiveSystemPrompt)
                        .model(modelResolver.resolve(executionModel))
                        .toolkit(toolkit)
                        .stateStore(stateStore)
                        .maxIters(spec.executionPolicy().maxIterations())
                        .maxRetries(spec.executionPolicy().maxModelRetries())
                        .disableMemoryTools()
                        .disableMemoryHooks()
                        .disableWorkspaceContext()
                        .disableAtPathExpansion()
                        .disableSubagents()
                        .disableDynamicSubagents()
                        .disableDynamicSkills()
                        .disableDefaultWorkspaceSkills()
                        .disableToolsConfig()
                        .disableFilesystemTools()
                        .disableShellTool()
                        .disableCompaction()
                        .disableToolResultEviction()
                        .skillsEnabled(false)
                        .enableAgentTracingLog(false)
                        .build();
        if (agent.getStateStore() != stateStore) {
            agent.close();
            throw new IllegalStateException("HarnessAgent 未使用外部注入的 AgentStateStore");
        }
        return agent;
    }

    private HarnessAgent compileNew(
            AgentSpec spec, List<ToolRef> effectiveTools, String effectiveSystemPrompt) {
        var toolkit = toolkitFactory.create(effectiveTools);
        var agent =
                HarnessAgent.builder()
                        .agentId(spec.agentId().value())
                        .name(spec.name())
                        .description(spec.description())
                        .sysPrompt(effectiveSystemPrompt)
                        .model(modelResolver.resolve(spec.model()))
                        .toolkit(toolkit)
                        .stateStore(stateStore)
                        .maxIters(spec.executionPolicy().maxIterations())
                        .maxRetries(spec.executionPolicy().maxModelRetries())
                        .disableMemoryTools()
                        .disableMemoryHooks()
                        .disableWorkspaceContext()
                        .disableAtPathExpansion()
                        .disableSubagents()
                        .disableDynamicSubagents()
                        .disableDynamicSkills()
                        .disableDefaultWorkspaceSkills()
                        .disableToolsConfig()
                        .disableFilesystemTools()
                        .disableShellTool()
                        .disableCompaction()
                        .disableToolResultEviction()
                        .skillsEnabled(false)
                        .enableAgentTracingLog(false)
                        .build();
        if (agent.getStateStore() != stateStore) {
            agent.close();
            throw new IllegalStateException("HarnessAgent 未使用外部注入的 AgentStateStore");
        }
        return agent;
    }

    private static String appendPrompt(String basePrompt, String appendix) {
        var normalizedBase = Objects.requireNonNull(basePrompt, "basePrompt 不能为空").trim();
        var normalizedAppendix = Objects.requireNonNull(appendix, "appendix 不能为空").trim();
        return normalizedAppendix.isEmpty()
                ? normalizedBase
                : normalizedBase + "\n\n" + normalizedAppendix;
    }

    @Override
    public void close() {
        cache.values().forEach(HarnessAgent::close);
        cache.clear();
    }

    private record DefinitionKey(
            AgentId agentId,
            long version,
            List<ToolRef> effectiveTools,
            String effectiveSystemPrompt) {}
}
