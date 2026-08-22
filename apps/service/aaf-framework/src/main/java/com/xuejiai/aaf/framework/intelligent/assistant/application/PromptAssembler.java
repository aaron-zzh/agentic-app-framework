package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentExecutionCommand.RoleAssignment;
import com.xuejiai.aaf.framework.intelligent.agent.model.AgentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt.PromptLayerKind;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt.PromptLayerSource;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt.PromptSourceKind;
import com.xuejiai.aaf.framework.intelligent.agent.model.SkillExecutionProfile;
import com.xuejiai.aaf.framework.intelligent.agent.model.SubagentSpec;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent;
import com.xuejiai.aaf.framework.intelligent.assistant.model.InvocationPolicy;
import com.xuejiai.aaf.framework.intelligent.core.prompt.PromptTemplateService;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;

/** 选择受信来源并按固定顺序编译 System Prompt；不管理模板版本或持久化。 */
public final class PromptAssembler {

    private final PromptTemplateService promptTemplates;

    public PromptAssembler(PromptTemplateService promptTemplates) {
        this.promptTemplates = Objects.requireNonNull(promptTemplates, "promptTemplates 不能为空");
    }

    public CompiledSystemPrompt compileAssistant(AssistantPromptRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        var constitution = promptTemplates.requireActive(CompiledSystemPrompt.CONSTITUTION_NAME);
        var assistant = request.assistant();
        var persona = assistant.persona();
        var sources = new ArrayList<PromptLayerSource>();
        sources.add(
                source(
                        PromptLayerKind.CONSTITUTION,
                        PromptSourceKind.ENGINE_TEMPLATE,
                        constitution.name(),
                        Integer.toString(constitution.version()),
                        constitution.content()));
        sources.add(
                source(
                        PromptLayerKind.IDENTITY,
                        PromptSourceKind.AAF_POLICY,
                        request.executionSpec().identifier(),
                        "1",
                        """
                        ## 冻结执行身份
                        executionId：%s
                        Assistant：%s@%d
                        动态 Agent：%s
                        任务描述：%s
                        """
                                .formatted(
                                        request.executionId().value(),
                                        assistant.assistantId().value(),
                                        assistant.version().value(),
                                        request.executionSpec().identifier(),
                                        request.executionSpec().description())));
        if (personaApplies(request.invocationPolicy())) {
            sources.add(
                    source(
                            PromptLayerKind.IDENTITY,
                            PromptSourceKind.ASSISTANT_PERSONA,
                            persona.personaKey(),
                            Integer.toString(persona.personaRevision()),
                            """
                            ## Assistant Persona
                            personaKey：%s
                            名称：%s
                            定位：%s
                            人格：%s
                            表达风格：%s
                            基础约束：%s
                            """
                                    .formatted(
                                            persona.personaKey(),
                                            persona.name(),
                                            persona.description(),
                                            persona.personality(),
                                            persona.speakingStyle(),
                                            persona.instructions())));
        }
        sources.add(
                source(
                        PromptLayerKind.ROLE,
                        PromptSourceKind.ASSISTANT_ROLE,
                        request.roleAssignment().roleKey(),
                        Long.toString(assistant.version().value()),
                        roleContent(request.roleAssignment())));
        appendSkills(sources, request.skillExecutionProfile());
        sources.add(invocationSource(request.invocationPolicy(), request.executionIntent()));
        return CompiledSystemPrompt.compile(sources);
    }

    public CompiledSystemPrompt compileAgent(
            AgentSpec spec,
            String executionIdentity,
            SkillExecutionProfile skillExecutionProfile,
            InvocationPolicy invocationPolicy) {
        Objects.requireNonNull(spec, "spec 不能为空");
        Objects.requireNonNull(skillExecutionProfile, "skillExecutionProfile 不能为空");
        Objects.requireNonNull(invocationPolicy, "invocationPolicy 不能为空");
        var constitution = promptTemplates.requireActive(CompiledSystemPrompt.CONSTITUTION_NAME);
        var sources = new ArrayList<PromptLayerSource>();
        sources.add(
                source(
                        PromptLayerKind.CONSTITUTION,
                        PromptSourceKind.ENGINE_TEMPLATE,
                        constitution.name(),
                        Integer.toString(constitution.version()),
                        constitution.content()));
        sources.add(
                source(
                        PromptLayerKind.IDENTITY,
                        PromptSourceKind.AAF_POLICY,
                        requireMetadata(executionIdentity, "executionIdentity"),
                        "1",
                        """
                        ## 冻结执行身份
                        executionId：%s
                        Agent：%s@%d
                        """
                                .formatted(
                                        executionIdentity,
                                        spec.agentId().value(),
                                        spec.version())));
        sources.add(
                source(
                        PromptLayerKind.IDENTITY,
                        PromptSourceKind.AGENT_DEFINITION,
                        spec.agentId().value(),
                        Long.toString(spec.version()),
                        spec.systemPrompt()));
        appendSkills(sources, skillExecutionProfile);
        sources.add(invocationSource(invocationPolicy, null));
        return CompiledSystemPrompt.compile(sources);
    }

    private static void appendSkills(
            List<PromptLayerSource> sources, SkillExecutionProfile skillExecutionProfile) {
        for (var skill : skillExecutionProfile.activatedSkills()) {
            var version = skill.version();
            sources.add(
                    source(
                            PromptLayerKind.SKILL,
                            PromptSourceKind.SKILL_VERSION,
                            skill.code(),
                            "%d:%d:%d"
                                    .formatted(
                                            version.skillId(),
                                            version.versionId(),
                                            version.version()),
                            """
                            ## Activated Skill
                            code：%s
                            scope：%s
                            activation：%s

                            %s
                            """
                                    .formatted(
                                            skill.code(),
                                            skill.scope(),
                                            skill.activationMode(),
                                            skill.content())));
        }
    }

    /**
     * 人格与表达风格是否适用于当前调用阶段。
     *
     * <p>COORDINATOR 必须输出严格 JSON 的 CoordinationPlan，注入人格与表达风格会污染格式，宪章要求「严格结构化输出场景以输出合同为准」。
     * 其余阶段的产出会进入用户可见的答复或最终业务内容，人格必须保留。
     */
    private static boolean personaApplies(InvocationPolicy policy) {
        return policy != InvocationPolicy.COORDINATOR;
    }

    private static PromptLayerSource invocationSource(
            InvocationPolicy policy, ExecutionIntent executionIntent) {
        var content = new StringBuilder();
        content.append("## 受信调用策略\n");
        content.append("阶段：").append(policy).append('\n');
        content.append(policy.instruction()).append('\n');
        if (executionIntent != null) {
            var artifact = executionIntent.artifactPolicy();
            content.append("交互模式：").append(executionIntent.interactionMode()).append('\n');
            content.append("澄清策略：").append(executionIntent.clarificationPolicy()).append('\n');
            content.append("输出种类：").append(artifact.outputKind()).append('\n');
            content.append("规范媒体类型：").append(artifact.canonicalMediaType()).append('\n');
            content.append("持久化模式：").append(artifact.persistenceMode()).append('\n');
            content.append("发布策略：").append(artifact.publishPolicy()).append('\n');
        }
        content.append("本层只定义行为和输出契约；工具授权、人工批准、预算与持久化许可由 Prompt 外部确定性机制裁决。\n");
        return source(
                PromptLayerKind.INVOCATION_POLICY,
                PromptSourceKind.AAF_POLICY,
                "invocation:" + policy.name().toLowerCase(java.util.Locale.ROOT),
                policy.contractVersion(),
                content.toString());
    }

    private static String roleContent(RoleAssignment role) {
        return role.systemPromptAppendix();
    }

    private static PromptLayerSource source(
            PromptLayerKind kind,
            PromptSourceKind sourceKind,
            String sourceKey,
            String sourceVersion,
            String content) {
        return new PromptLayerSource(kind, sourceKind, sourceKey, sourceVersion, content);
    }

    private static String requireMetadata(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value;
    }

    public record AssistantPromptRequest(
            AssistantDefinition assistant,
            SubagentSpec.Dynamic executionSpec,
            RoleAssignment roleAssignment,
            SkillExecutionProfile skillExecutionProfile,
            InvocationPolicy invocationPolicy,
            ExecutionIntent executionIntent,
            ExecutionId executionId) {

        public AssistantPromptRequest {
            Objects.requireNonNull(assistant, "assistant 不能为空");
            Objects.requireNonNull(executionSpec, "executionSpec 不能为空");
            Objects.requireNonNull(roleAssignment, "roleAssignment 不能为空");
            Objects.requireNonNull(skillExecutionProfile, "skillExecutionProfile 不能为空");
            Objects.requireNonNull(invocationPolicy, "invocationPolicy 不能为空");
            Objects.requireNonNull(executionIntent, "executionIntent 不能为空");
            Objects.requireNonNull(executionId, "executionId 不能为空");
        }
    }
}
