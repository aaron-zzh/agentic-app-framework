package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.compiler;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentSpec;
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
    private final ConcurrentMap<DefinitionKey, HarnessAgent> cache = new ConcurrentHashMap<>();

    public AgentScopeSpecCompiler(
            AgentStateStore stateStore,
            AgentScopeToolkitFactory toolkitFactory,
            AgentScopeModelResolver modelResolver) {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore 不能为空");
        this.toolkitFactory = Objects.requireNonNull(toolkitFactory, "toolkitFactory 不能为空");
        this.modelResolver = Objects.requireNonNull(modelResolver, "modelResolver 不能为空");
    }

    /** 按 agentId + version 命中不可变编译产物。 */
    public HarnessAgent compile(AgentSpec spec) {
        Objects.requireNonNull(spec, "spec 不能为空");
        var key = new DefinitionKey(spec.agentId(), spec.version());
        return cache.computeIfAbsent(key, ignored -> compileNew(spec));
    }

    private HarnessAgent compileNew(AgentSpec spec) {
        var toolkit = toolkitFactory.create(spec.tools());
        var agent =
                HarnessAgent.builder()
                        .agentId(spec.agentId().value())
                        .name(spec.name())
                        .description(spec.description())
                        .sysPrompt(spec.systemPrompt())
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

    @Override
    public void close() {
        cache.values().forEach(HarnessAgent::close);
        cache.clear();
    }

    private record DefinitionKey(AgentId agentId, long version) {}
}
