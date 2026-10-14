package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.xuejiai.aaf.framework.intelligent.agent.model.ActivatedSkill;
import com.xuejiai.aaf.framework.intelligent.agent.model.AgentExecutionCommand.RoleAssignment;
import com.xuejiai.aaf.framework.intelligent.agent.model.AgentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt.PromptLayerKind;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt.PromptLayerSource;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt.PromptSourceKind;
import com.xuejiai.aaf.framework.intelligent.agent.model.SkillExecutionProfile;
import com.xuejiai.aaf.framework.intelligent.agent.model.SubagentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.port.SkillReferenceCatalogPort;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionCriteria;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent;
import com.xuejiai.aaf.framework.intelligent.assistant.model.InvocationPolicy;
import com.xuejiai.aaf.framework.intelligent.core.prompt.PromptTemplateService;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillReference;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillReference.LoadMode;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;

/** 选择受信来源并按固定顺序编译 System Prompt；不管理模板版本或持久化。 */
public final class PromptAssembler {

    private final PromptTemplateService promptTemplates;
    private final SkillReferenceCatalogPort skillReferences;

    public PromptAssembler(
            PromptTemplateService promptTemplates, SkillReferenceCatalogPort skillReferences) {
        this.promptTemplates = Objects.requireNonNull(promptTemplates, "promptTemplates 不能为空");
        this.skillReferences = Objects.requireNonNull(skillReferences, "skillReferences 不能为空");
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
        sources.add(
                invocationSource(
                        request.invocationPolicy(),
                        request.executionIntent(),
                        request.completionCriteria()));
        sources.add(
                source(
                        PromptLayerKind.ROLE,
                        PromptSourceKind.ASSISTANT_ROLE,
                        request.roleAssignment().roleKey(),
                        Long.toString(assistant.version().value()),
                        roleContent(request.roleAssignment())));
        appendSkills(sources, request.skillExecutionProfile());
        sources.add(
                source(
                        PromptLayerKind.PERSONA,
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
        sources.add(invocationSource(invocationPolicy, null, null));
        appendSkills(sources, skillExecutionProfile);
        return CompiledSystemPrompt.compile(sources);
    }

    private void appendSkills(
            List<PromptLayerSource> sources, SkillExecutionProfile skillExecutionProfile) {
        var versionIds =
                skillExecutionProfile.activatedSkills().stream()
                        .map(skill -> skill.version().versionId())
                        .collect(Collectors.toUnmodifiableSet());
        var referencesByVersion = skillReferences.findByVersionIds(versionIds);
        var documentReferences =
                referencesByVersion.values().stream()
                        .flatMap(List::stream)
                        .map(
                                reference ->
                                        new SkillReferenceCatalogPort.DocumentReference(
                                                reference.documentId(),
                                                reference.documentVersionId()))
                        .collect(Collectors.toUnmodifiableSet());
        var documentContents = skillReferences.readContents(documentReferences);
        for (var skill : skillExecutionProfile.activatedSkills()) {
            var version = skill.version();
            var references =
                    referencesByVersion.getOrDefault(version.versionId(), List.of()).stream()
                            .map(reference -> resolveReference(skill, reference, documentContents))
                            .toList();
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
                                            runtimeSkillContent(skill, references))));
        }
    }

    private static ResolvedReference resolveReference(
            ActivatedSkill skill,
            SkillReference reference,
            Map<SkillReferenceCatalogPort.DocumentReference, String> documentContents) {
        var loadKey = reference.loadKey(skill.code(), skill.version().versionId());
        var content =
                documentContents.get(
                        new SkillReferenceCatalogPort.DocumentReference(
                                reference.documentId(), reference.documentVersionId()));
        if (content == null) {
            return unavailableReference(reference, loadKey, "文档正文不可用", null);
        }
        try {
            return new ResolvedReference(
                    reference, loadKey, reference.requireLoadableContent(content), null);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return unavailableReference(reference, loadKey, "文档正文不满足发布约束", failure);
        }
    }

    private static ResolvedReference unavailableReference(
            SkillReference reference, String loadKey, String reason, RuntimeException failure) {
        if (reference.required()) {
            throw new IllegalStateException("必需 Skill Reference 不可用: " + loadKey, failure);
        }
        return new ResolvedReference(reference, loadKey, null, reason);
    }

    private static String runtimeSkillContent(
            ActivatedSkill skill, List<ResolvedReference> references) {
        if (references.isEmpty()) {
            return skill.content();
        }
        var content = new StringBuilder(skill.content());
        content.append("\n\n## Skill References\n");
        content.append(
                "以下清单由服务端根据冻结 SkillVersion 生成。仅按 loadWhen 加载必要的 ON_DEMAND 文档，并原样使用 loadKey。\n");
        for (var resolved : references) {
            var reference = resolved.reference();
            content.append("\n### ").append(reference.title()).append('\n');
            content.append("- referenceKey：`").append(reference.referenceKey()).append("`\n");
            content.append("- loadMode：`").append(reference.loadMode()).append("`\n");
            content.append("- loadWhen：").append(reference.loadWhen()).append('\n');
            content.append("- required：").append(reference.required()).append('\n');
            content.append("- maxTokens：").append(reference.maxTokens()).append('\n');
            content.append("- available：").append(resolved.available()).append('\n');
            if (resolved.available() && reference.loadMode() == LoadMode.ON_DEMAND) {
                content.append("- loadKey：`").append(resolved.loadKey()).append("`\n");
            }
            if (!resolved.available()) {
                content.append("- unavailableReason：")
                        .append(resolved.unavailableReason())
                        .append('\n');
            }
        }
        references.stream()
                .filter(ResolvedReference::available)
                .filter(reference -> reference.reference().loadMode() == LoadMode.ON_ACTIVATION)
                .forEach(
                        reference -> {
                            content.append("\n## Activated Reference：")
                                    .append(reference.reference().title())
                                    .append("\n\n")
                                    .append(reference.content());
                        });
        return content.toString();
    }

    private static PromptLayerSource invocationSource(
            InvocationPolicy policy,
            ExecutionIntent executionIntent,
            CompletionCriteria completionCriteria) {
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
            if (policy == InvocationPolicy.COORDINATOR) {
                // 分解策略只改变协调者的工具面；非 AUTO 时明示，避免与上方"简单任务直接回复"的通用规则冲突
                switch (executionIntent.decomposition()) {
                    case NONE -> content.append("分解策略：NONE。本次不得委派子智能体，直接在本次回复中完成并交付。\n");
                    case REQUIRED ->
                            content.append(
                                    "分解策略：REQUIRED。本次必须调用 delegate_agents 委派子智能体，不得直接回答，"
                                            + "本次回复不要输出面向用户的正文；该策略覆盖上方关于简单任务直接回复的规则。\n");
                    case AUTO -> {}
                }
            }
        }
        appendCompletionContract(content, completionCriteria);
        content.append("本层只定义行为和输出契约；工具授权、人工批准、预算与持久化许可由 Prompt 外部确定性机制裁决。\n");
        return source(
                PromptLayerKind.INVOCATION_POLICY,
                PromptSourceKind.AAF_POLICY,
                "invocation:" + policy.name().toLowerCase(java.util.Locale.ROOT),
                policy.contractVersion(),
                content.toString());
    }

    /**
     * 渲染显式完成标准。
     *
     * <p>只暴露模型能影响的部分：完成语义和产物断言。{@code requiredEventTypes} 是服务端事件流断言，模型无法直接产生也无法验证，打印它只会增加噪声和误导。
     */
    private static void appendCompletionContract(
            StringBuilder content, CompletionCriteria completionCriteria) {
        if (completionCriteria == null) {
            return;
        }
        content.append("完成标准：")
                .append(
                        switch (completionCriteria.kind()) {
                            case RESPONSE_DELIVERED -> "交付一次满足上述输出契约的完整答复";
                            case REVERSIBLE_DRAFT_CREATED -> "产出可撤销草稿，并确认保存动作真实成功";
                            case CUSTOM -> "满足下列产物断言";
                        })
                .append('\n');
        if (!completionCriteria.requiredPayloadValues().isEmpty()) {
            content.append("产物断言：")
                    .append(
                            completionCriteria.requiredPayloadValues().entrySet().stream()
                                    .sorted(Map.Entry.comparingByKey())
                                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                                    .collect(Collectors.joining("、")))
                    .append('\n');
        }
        content.append("生成内容不等于完成任务；未满足完成标准时必须如实报告未完成。\n");
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

    private record ResolvedReference(
            SkillReference reference, String loadKey, String content, String unavailableReason) {

        private boolean available() {
            return content != null;
        }
    }

    public record AssistantPromptRequest(
            AssistantDefinition assistant,
            SubagentSpec.Dynamic executionSpec,
            RoleAssignment roleAssignment,
            SkillExecutionProfile skillExecutionProfile,
            InvocationPolicy invocationPolicy,
            ExecutionIntent executionIntent,
            CompletionCriteria completionCriteria,
            ExecutionId executionId) {

        public AssistantPromptRequest {
            Objects.requireNonNull(assistant, "assistant 不能为空");
            Objects.requireNonNull(executionSpec, "executionSpec 不能为空");
            Objects.requireNonNull(roleAssignment, "roleAssignment 不能为空");
            Objects.requireNonNull(skillExecutionProfile, "skillExecutionProfile 不能为空");
            Objects.requireNonNull(invocationPolicy, "invocationPolicy 不能为空");
            Objects.requireNonNull(executionIntent, "executionIntent 不能为空");
            Objects.requireNonNull(completionCriteria, "completionCriteria 不能为空");
            Objects.requireNonNull(executionId, "executionId 不能为空");
        }
    }
}
