package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt.PromptLayerKind;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt.PromptLayerSource;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt.PromptSourceKind;
import com.xuejiai.aaf.framework.intelligent.agent.model.ExecutionPolicy;
import com.xuejiai.aaf.framework.intelligent.agent.model.SubagentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolCatalogPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolCatalogPort.ToolDefinition;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolGatewayPort;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelSpec;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.model.AgentScopeModelResolver;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.tool.AgentScopeToolkitFactory;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.tool.ToolResultEvidenceStore;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AgentId;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;

class AgentScopeSpecCompilerTest extends BaseMockitoUnitTest {

    @Mock private AgentStateStore stateStore;
    @Mock private ToolCatalogPort toolCatalog;
    @Mock private ToolGatewayPort toolGateway;
    @Mock private AgentScopeModelResolver modelResolver;
    @Mock private Model model;

    private AgentScopeSpecCompiler compiler;

    @BeforeEach
    void setUp() {
        var toolkitFactory =
                new AgentScopeToolkitFactory(
                        toolCatalog, toolGateway, new ToolResultEvidenceStore());
        compiler = new AgentScopeSpecCompiler(stateStore, toolkitFactory, modelResolver);
        when(modelResolver.resolve(any(ModelSpec.class))).thenReturn(model);
        when(toolCatalog.resolve(any()))
                .thenAnswer(
                        invocation -> {
                            List<ToolRef> refs = invocation.getArgument(0);
                            return refs.stream().map(this::definition).toList();
                        });
    }

    @AfterEach
    void tearDown() {
        compiler.close();
    }

    @Test
    @DisplayName("Given Agent 声明两个工具但最终画像只有一个 When 编译 Then Toolkit 只注册最终工具")
    void should_register_only_effective_tools() {
        var search = tool("knowledge.search");
        var generate = tool("content.generate");
        var spec = agentSpec(List.of(search, generate));

        var agent =
                compiler.compile(spec, compiled("agent.compiler-test", "技能提示"), List.of(search));

        assertThat(spec.tools()).hasSize(2);
        assertThat(agent.getToolkit().getToolNames())
                .contains(search.name())
                .doesNotContain(generate.name());
        assertThat(agent.getDelegate().getSysPrompt()).contains("技能提示");
    }

    @Test
    @DisplayName("Given 相同完整执行画像 When 重复编译 Then 复用同一 Predefined Agent")
    void should_reuse_predefined_agent_for_same_execution_profile() {
        var search = tool("knowledge.search");
        var spec = agentSpec(List.of(search));

        var first =
                compiler.compile(spec, compiled("agent.compiler-test", "稳定技能提示"), List.of(search));
        var second =
                compiler.compile(spec, compiled("agent.compiler-test", "稳定技能提示"), List.of(search));

        assertThat(second).isSameAs(first);
    }

    @Test
    @DisplayName("Given 相同默认 Role 执行画像 When 重复直接编译 Then 复用同一主助理")
    void should_reuse_direct_agent_for_same_execution_profile() {
        var search = tool("knowledge.search");
        var spec = dynamicSpec(List.of(search));
        var executionModel = new ModelSpec("1");

        var first =
                compiler.compileDirect(
                        spec,
                        executionModel,
                        compiled("agent.dynamic-test", "Role 与技能提示"),
                        List.of(search));
        var second =
                compiler.compileDirect(
                        spec,
                        executionModel,
                        compiled("agent.dynamic-test", "Role 与技能提示"),
                        List.of(search));

        assertThat(second).isSameAs(first);
    }

    @Test
    @DisplayName("Given 相同定义但不同技能提示 When 编译 Then 缓存产物相互隔离")
    void should_isolate_predefined_cache_by_effective_prompt() {
        var spec = agentSpec(List.of());

        var first = compiler.compile(spec, compiled("agent.compiler-test", "技能 A"), List.of());
        var second = compiler.compile(spec, compiled("agent.compiler-test", "技能 B"), List.of());

        assertThat(second).isNotSameAs(first);
        assertThat(first.getDelegate().getSysPrompt()).contains("技能 A");
        assertThat(second.getDelegate().getSysPrompt()).contains("技能 B");
    }

    @Test
    @DisplayName("Given 并发请求使用不同 Role 工具画像 When 编译 Then Toolkit 不串用")
    void should_isolate_concurrent_predefined_profiles_by_effective_tools() {
        var search = tool("knowledge.search");
        var generate = tool("content.generate");
        var spec = agentSpec(List.of(search, generate));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var searchFuture =
                    CompletableFuture.supplyAsync(
                            () ->
                                    compiler.compile(
                                            spec,
                                            compiled("agent.compiler-test", "并发测试"),
                                            List.of(search)),
                            executor);
            var generateFuture =
                    CompletableFuture.supplyAsync(
                            () ->
                                    compiler.compile(
                                            spec,
                                            compiled("agent.compiler-test", "并发测试"),
                                            List.of(generate)),
                            executor);

            var searchAgent = searchFuture.join();
            var generateAgent = generateFuture.join();

            assertThat(generateAgent).isNotSameAs(searchAgent);
            assertThat(searchAgent.getToolkit().getToolNames())
                    .contains(search.name())
                    .doesNotContain(generate.name());
            assertThat(generateAgent.getToolkit().getToolNames())
                    .contains(generate.name())
                    .doesNotContain(search.name());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Given 并发 Dynamic 请求画像不同 When 编译 Then 每次新建且 Prompt 与工具不串用")
    void should_create_isolated_dynamic_agents_for_concurrent_profiles() {
        var search = tool("knowledge.search");
        var generate = tool("content.generate");
        var spec = dynamicSpec(List.of(search, generate));
        var executionModel = new ModelSpec("1");
        var executor = Executors.newFixedThreadPool(2);
        HarnessAgent searchAgent = null;
        HarnessAgent generateAgent = null;
        try {
            var searchFuture =
                    CompletableFuture.supplyAsync(
                            () ->
                                    compiler.compileDynamic(
                                            spec,
                                            executionModel,
                                            compiled("agent.dynamic-test", "技能 A"),
                                            List.of(search)),
                            executor);
            var generateFuture =
                    CompletableFuture.supplyAsync(
                            () ->
                                    compiler.compileDynamic(
                                            spec,
                                            executionModel,
                                            compiled("agent.dynamic-test", "技能 B"),
                                            List.of(generate)),
                            executor);

            searchAgent = searchFuture.join();
            generateAgent = generateFuture.join();

            assertThat(generateAgent).isNotSameAs(searchAgent);
            assertThat(searchAgent.getToolkit().getToolNames())
                    .contains(search.name())
                    .doesNotContain(generate.name());
            assertThat(generateAgent.getToolkit().getToolNames())
                    .contains(generate.name())
                    .doesNotContain(search.name());
            assertThat(searchAgent.getDelegate().getSysPrompt()).contains("技能 A");
            assertThat(generateAgent.getDelegate().getSysPrompt()).contains("技能 B");
        } finally {
            if (searchAgent != null) {
                searchAgent.close();
            }
            if (generateAgent != null) {
                generateAgent.close();
            }
            executor.shutdownNow();
        }
    }

    private CompiledSystemPrompt compiled(String identity, String content) {
        return CompiledSystemPrompt.compile(
                List.of(
                        new PromptLayerSource(
                                PromptLayerKind.CONSTITUTION,
                                PromptSourceKind.ENGINE_TEMPLATE,
                                CompiledSystemPrompt.CONSTITUTION_NAME,
                                "1",
                                "测试 Constitution"),
                        new PromptLayerSource(
                                PromptLayerKind.IDENTITY,
                                PromptSourceKind.AAF_POLICY,
                                identity,
                                "1",
                                content),
                        new PromptLayerSource(
                                PromptLayerKind.IDENTITY,
                                PromptSourceKind.ASSISTANT_ACTOR,
                                "assistant.test",
                                "1",
                                "测试 Actor"),
                        new PromptLayerSource(
                                PromptLayerKind.INVOCATION_POLICY,
                                PromptSourceKind.AAF_POLICY,
                                "invocation:test",
                                "1",
                                "测试调用策略")));
    }

    private AgentSpec agentSpec(List<ToolRef> tools) {
        return new AgentSpec(
                new AgentId("agent.compiler-test"),
                1L,
                "编译器测试 Agent",
                "验证角色工具限制",
                "仅执行测试允许的工具",
                new ModelSpec("1"),
                tools,
                policy());
    }

    private SubagentSpec.Dynamic dynamicSpec(List<ToolRef> tools) {
        return new SubagentSpec.Dynamic(
                "agent.dynamic-test",
                "验证动态执行画像",
                "动态基础提示",
                tools,
                policy(),
                com.xuejiai.aaf.framework.intelligent.agent.model.ModelSelectionRequirement
                        .balanced(),
                false);
    }

    private ExecutionPolicy policy() {
        return new ExecutionPolicy(3, 1, Duration.ofSeconds(30));
    }

    private ToolDefinition definition(ToolRef ref) {
        return new ToolDefinition(
                ref,
                ref.name() + "描述",
                Map.of("type", "object"),
                "LOCAL",
                "",
                true,
                false,
                false,
                false);
    }

    private ToolRef tool(String name) {
        return new ToolRef("tool." + name, 1L, name);
    }
}
