package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.compiler;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt;
import com.xuejiai.aaf.framework.intelligent.agent.model.SubagentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelSpec;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.middleware.PromptEnvelopeCaptureMiddleware;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.model.AgentScopeModelResolver;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.tool.AgentScopeToolkitFactory;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AgentId;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.extern.slf4j.Slf4j;

/**
 * 将版本化 AAF AgentSpec 编译并缓存为无状态 HarnessAgent。
 *
 * <p>HarnessAgent 是无状态引擎：实例只持有不可变配置（system prompt / 模型 / 工具集）， 会话数据由 AgentStateStore 按 (userId,
 * sessionId) 寻址，因此同一执行画像可跨请求共享一个实例。
 */
@Slf4j
public final class AgentScopeSpecCompiler implements AutoCloseable {

    private final AgentStateStore stateStore;
    private final AgentScopeToolkitFactory toolkitFactory;
    private final AgentScopeModelResolver modelResolver;
    private final PromptEnvelopeCaptureMiddleware envelopeCapture;

    /** 预定义 Agent 缓存：键含版本号与生效画像，画像变化即视为新条目。 */
    private final ConcurrentMap<DefinitionKey, HarnessAgent> cache = new ConcurrentHashMap<>();

    /** 主助理直答缓存：动态规格无版本号，用标识 + 模型 + 画像作键。 */
    private final ConcurrentMap<DirectKey, HarnessAgent> directCache = new ConcurrentHashMap<>();

    public AgentScopeSpecCompiler(
            AgentStateStore stateStore,
            AgentScopeToolkitFactory toolkitFactory,
            AgentScopeModelResolver modelResolver,
            PromptEnvelopeCaptureMiddleware envelopeCapture) {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore 不能为空");
        this.toolkitFactory = Objects.requireNonNull(toolkitFactory, "toolkitFactory 不能为空");
        this.modelResolver = Objects.requireNonNull(modelResolver, "modelResolver 不能为空");
        this.envelopeCapture = Objects.requireNonNull(envelopeCapture, "envelopeCapture 不能为空");
    }

    /** 按完整不可变执行画像命中预定义 Agent 编译产物。 */
    public HarnessAgent compile(
            AgentSpec spec,
            CompiledSystemPrompt compiledSystemPrompt,
            List<ToolRef> effectiveTools) {
        Objects.requireNonNull(spec, "spec 不能为空");
        Objects.requireNonNull(compiledSystemPrompt, "compiledSystemPrompt 不能为空");
        compiledSystemPrompt.verify();
        var finalTools = List.copyOf(Objects.requireNonNull(effectiveTools, "effectiveTools 不能为空"));
        var key =
                new DefinitionKey(
                        spec.agentId(),
                        spec.version(),
                        finalTools,
                        compiledSystemPrompt.sha256(),
                        compiledSystemPrompt.content());
        return cache.computeIfAbsent(
                key, ignored -> compileNew(spec, finalTools, compiledSystemPrompt.content()));
    }

    /** 按完整任务执行画像编译并缓存默认 Role 的主助理执行体。 */
    public HarnessAgent compileDirect(
            SubagentSpec.Dynamic spec,
            ModelSpec executionModel,
            CompiledSystemPrompt compiledSystemPrompt,
            List<ToolRef> effectiveTools) {
        var resolved = resolveDynamic(spec, executionModel, compiledSystemPrompt, effectiveTools);
        var key =
                new DirectKey(
                        spec.identifier(),
                        executionModel,
                        resolved.tools(),
                        compiledSystemPrompt.sha256(),
                        resolved.systemPrompt());
        return directCache.computeIfAbsent(
                key,
                ignored ->
                        compileDynamicNew(
                                spec, executionModel, resolved.tools(), resolved.systemPrompt()));
    }

    /** 现场编译动态子智能体；规格没有稳定版本键，因此不进入定义缓存。 */
    public HarnessAgent compileDynamic(
            SubagentSpec.Dynamic spec,
            ModelSpec executionModel,
            CompiledSystemPrompt compiledSystemPrompt,
            List<ToolRef> effectiveTools) {
        var resolved = resolveDynamic(spec, executionModel, compiledSystemPrompt, effectiveTools);
        log.debug(
                "[AgentScope编译] 现场编译 AAF 动态委托 HarnessAgent：identifier={}，模型={}，工具数={}；Harness 内建子智能体、动态技能、记忆和工作区能力均已关闭",
                spec.identifier(),
                executionModel.modelId(),
                resolved.tools().size());
        return compileDynamicNew(spec, executionModel, resolved.tools(), resolved.systemPrompt());
    }

    /** 校验动态规格并使用 AAF 已计算的最终工具集。 */
    private DynamicExecutionProfile resolveDynamic(
            SubagentSpec.Dynamic spec,
            ModelSpec executionModel,
            CompiledSystemPrompt compiledSystemPrompt,
            List<ToolRef> effectiveTools) {
        Objects.requireNonNull(spec, "spec 不能为空");
        Objects.requireNonNull(executionModel, "executionModel 不能为空");
        Objects.requireNonNull(compiledSystemPrompt, "compiledSystemPrompt 不能为空");
        compiledSystemPrompt.verify();
        effectiveTools = List.copyOf(Objects.requireNonNull(effectiveTools, "effectiveTools 不能为空"));
        if (spec.inheritParentTools()) {
            throw new IllegalArgumentException("Dynamic 子智能体暂不支持继承父 Agent 工具");
        }
        return new DynamicExecutionProfile(effectiveTools, compiledSystemPrompt.content());
    }

    private HarnessAgent compileDynamicNew(
            SubagentSpec.Dynamic spec,
            ModelSpec executionModel,
            List<ToolRef> effectiveTools,
            String effectiveSystemPrompt) {
        var toolkit = toolkitFactory.create(effectiveTools);
        // Harness 内置能力（工作区 / 记忆 / 子智能体 / 技能 / 文件与 Shell / 压缩）全部关闭：
        // AAF 自己承担这些职责，只借用 ReAct 推理循环 + 工具调用，避免出现第二套真理源
        var agent =
                HarnessAgent.builder()
                        .agentId(spec.identifier())
                        .name(spec.name())
                        .description(spec.description())
                        .sysPrompt(effectiveSystemPrompt)
                        .model(modelResolver.resolve(executionModel))
                        .toolkit(toolkit)
                        .stateStore(stateStore)
                        .middleware(envelopeCapture)
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
        // 兜底断言：状态必须落在共享 Redis Store，否则多副本会退化成本地 JsonFile 存储
        if (agent.getStateStore() != stateStore) {
            agent.close();
            throw new IllegalStateException("HarnessAgent 未使用外部注入的 AgentStateStore");
        }
        return agent;
    }

    private HarnessAgent compileNew(
            AgentSpec spec, List<ToolRef> effectiveTools, String effectiveSystemPrompt) {
        var toolkit = toolkitFactory.create(effectiveTools);
        // 关闭项含义同 compileDynamicNew
        var agent =
                HarnessAgent.builder()
                        .agentId(spec.agentId().value())
                        .name(spec.name())
                        .description(spec.description())
                        .sysPrompt(effectiveSystemPrompt)
                        .model(modelResolver.resolve(spec.model()))
                        .toolkit(toolkit)
                        .stateStore(stateStore)
                        .middleware(envelopeCapture)
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
        // 兜底断言：状态必须落在共享 Redis Store，否则多副本会退化成本地 JsonFile 存储
        if (agent.getStateStore() != stateStore) {
            agent.close();
            throw new IllegalStateException("HarnessAgent 未使用外部注入的 AgentStateStore");
        }
        return agent;
    }

    /** 容器销毁时释放全部缓存实例；一次性动态子智能体由调用方自行 close。 */
    @Override
    public void close() {
        cache.values().forEach(HarnessAgent::close);
        cache.clear();
        directCache.values().forEach(HarnessAgent::close);
        directCache.clear();
    }

    /** 预定义 Agent 缓存键。 */
    private record DefinitionKey(
            AgentId agentId,
            long version,
            List<ToolRef> effectiveTools,
            String promptSha256,
            String promptContent) {}

    /** 主助理直答缓存键。 */
    private record DirectKey(
            String identifier,
            ModelSpec model,
            List<ToolRef> effectiveTools,
            String promptSha256,
            String promptContent) {}

    /** 动态规格的生效画像。 */
    private record DynamicExecutionProfile(List<ToolRef> tools, String systemPrompt) {}
}
