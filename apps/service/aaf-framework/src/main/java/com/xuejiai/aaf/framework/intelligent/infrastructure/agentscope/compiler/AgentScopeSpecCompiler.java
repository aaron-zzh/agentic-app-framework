package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.compiler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt;
import com.xuejiai.aaf.framework.intelligent.agent.model.ExecutionPolicy;
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
 *
 * <p>两个缓存都是有界 LRU（RQ-06）：画像持续抖动（prompt / 模型 / 工具变化）时旧实例按最近最少使用淘汰并 close，不再等到容器关闭才回收。
 */
@Slf4j
public final class AgentScopeSpecCompiler implements AutoCloseable {

    /** 单个缓存的默认容量；正常部署的并发执行画像数远低于该值，触及上限即说明画像抖动异常。 */
    public static final int DEFAULT_CACHE_CAPACITY = 128;

    private final AgentStateStore stateStore;
    private final AgentScopeToolkitFactory toolkitFactory;
    private final AgentScopeModelResolver modelResolver;
    private final PromptEnvelopeCaptureMiddleware envelopeCapture;

    /** 预定义 Agent 缓存：键含版本号与生效画像，画像变化即视为新条目。 */
    private final BoundedAgentCache<DefinitionKey> cache;

    /** 主助理直答缓存：动态规格无版本号，用标识 + 模型 + 画像 + 执行策略作键。 */
    private final BoundedAgentCache<DirectKey> directCache;

    public AgentScopeSpecCompiler(
            AgentStateStore stateStore,
            AgentScopeToolkitFactory toolkitFactory,
            AgentScopeModelResolver modelResolver,
            PromptEnvelopeCaptureMiddleware envelopeCapture,
            int cacheCapacity) {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore 不能为空");
        this.toolkitFactory = Objects.requireNonNull(toolkitFactory, "toolkitFactory 不能为空");
        this.modelResolver = Objects.requireNonNull(modelResolver, "modelResolver 不能为空");
        this.envelopeCapture = Objects.requireNonNull(envelopeCapture, "envelopeCapture 不能为空");
        this.cache = new BoundedAgentCache<>("predefined", cacheCapacity);
        this.directCache = new BoundedAgentCache<>("direct", cacheCapacity);
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
        // 执行策略进入缓存键（RQ-07）：maxIterations / maxRetries 已写进 HarnessAgent 不可变配置，
        // 若不纳入键，策略不同的两次直答会复用首次编译的迭代与重试上限，形成执行策略内部不一致
        var key =
                new DirectKey(
                        spec.identifier(),
                        executionModel,
                        resolved.tools(),
                        compiledSystemPrompt.sha256(),
                        resolved.systemPrompt(),
                        spec.executionPolicy());
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
        cache.closeAll();
        directCache.closeAll();
    }

    /** 当前缓存条目数，供测试与监控确认缓存有界。 */
    public int cachedAgentCount() {
        return cache.size() + directCache.size();
    }

    /** 预定义 Agent 缓存键。 */
    private record DefinitionKey(
            AgentId agentId,
            long version,
            List<ToolRef> effectiveTools,
            String promptSha256,
            String promptContent) {}

    /** 主助理直答缓存键；执行策略是 HarnessAgent 不可变配置的一部分，必须进键。 */
    private record DirectKey(
            String identifier,
            ModelSpec model,
            List<ToolRef> effectiveTools,
            String promptSha256,
            String promptContent,
            ExecutionPolicy executionPolicy) {}

    /** 动态规格的生效画像。 */
    private record DynamicExecutionProfile(List<ToolRef> tools, String systemPrompt) {}

    /**
     * 有界 LRU Agent 缓存：淘汰时立即 close 被淘汰实例，避免堆内 Agent / Toolkit / prompt 无界增长（RQ-06）。
     *
     * <p>用 {@code synchronized} 包裹 access-order {@link LinkedHashMap}：编译是纯内存操作（工具装配 + 模型解析），
     * 串行代价可忽略，换来的是"命中即刷新 LRU 顺序、超限即淘汰并 close"的确定性语义。
     */
    private static final class BoundedAgentCache<K> {

        private final String name;
        private final int capacity;
        private final LinkedHashMap<K, HarnessAgent> entries;

        private BoundedAgentCache(String name, int capacity) {
            if (capacity < 1) {
                throw new IllegalArgumentException("cacheCapacity 必须大于 0");
            }
            this.name = name;
            this.capacity = capacity;
            this.entries =
                    new LinkedHashMap<>(16, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<K, HarnessAgent> eldest) {
                            if (size() <= BoundedAgentCache.this.capacity) {
                                return false;
                            }
                            log.warn(
                                    "[AgentScope编译] {} 缓存达到上限，淘汰最近最少使用实例并释放：上限={}",
                                    BoundedAgentCache.this.name,
                                    BoundedAgentCache.this.capacity);
                            eldest.getValue().close();
                            return true;
                        }
                    };
        }

        private synchronized HarnessAgent computeIfAbsent(
                K key, Function<K, HarnessAgent> factory) {
            var existing = entries.get(key);
            if (existing != null) {
                return existing;
            }
            var created = factory.apply(key);
            entries.put(key, created);
            return created;
        }

        private synchronized void closeAll() {
            entries.values().forEach(HarnessAgent::close);
            entries.clear();
        }

        private synchronized int size() {
            return entries.size();
        }
    }
}
