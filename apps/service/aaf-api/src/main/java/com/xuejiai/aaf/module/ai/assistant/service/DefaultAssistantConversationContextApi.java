package com.xuejiai.aaf.module.ai.assistant.service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentExecutionCommand.RoleAssignment;
import com.xuejiai.aaf.framework.intelligent.assistant.model.PersonaSnapshot;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Role;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.module.ai.assistant.api.AssistantConversationContextApi;
import com.xuejiai.aaf.module.knowledge.api.KnowledgeSearchApi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 默认 Assistant 实时会话上下文解析实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAssistantConversationContextApi implements AssistantConversationContextApi {

    private static final int MAX_KNOWLEDGE_BASES = 6;

    private final AssistantDefinitionPort assistantDefinitions;
    private final KnowledgeSearchApi knowledgeSearchApi;

    @Override
    public ConversationContext resolve(ConversationIdentity identity, String roleKey) {
        Objects.requireNonNull(identity, "identity 不能为空");
        var definition =
                OrgContext.runIgnoring(
                        () ->
                                assistantDefinitions
                                        .findDefaultForUser(
                                                new TenantId(identity.orgId().toString()),
                                                new UserId(identity.ownerUserId().toString()))
                                        .orElseThrow(
                                                () ->
                                                        new IllegalStateException(
                                                                "当前用户默认 Assistant 不存在")));
        final Role role;
        if (roleKey == null) {
            role = definition.defaultRole();
        } else {
            try {
                role = definition.requireRole(roleKey.trim());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("指定 Role 未绑定、未启用或不可用", exception);
            }
        }
        var knowledgeBaseIds =
                mergeKnowledgeBaseIds(
                        definition.assistantId().value(),
                        definition.knowledgeBaseIds(),
                        role.knowledgeBaseIds());
        var knowledgeBases = knowledgeSearchApi.findKnowledgeBases(knowledgeBaseIds);
        return new ConversationContext(
                definition.assistantId().value(),
                definition.version().value(),
                role.key(),
                composeInstructions(definition.persona(), role, knowledgeBases),
                knowledgeBaseIds);
    }

    private Set<UUID> mergeKnowledgeBaseIds(
            String assistantId, Set<UUID> assistantIds, Set<UUID> roleIds) {
        var merged = new LinkedHashSet<UUID>();
        assistantIds.stream().sorted().forEach(merged::add);
        roleIds.stream().sorted().forEach(merged::add);
        var selected =
                merged.stream()
                        .limit(MAX_KNOWLEDGE_BASES)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        var omittedCount = merged.size() - selected.size();
        if (omittedCount > 0) {
            log.info(
                    "Realtime 默认知识库超过上限: assistantId={}, omittedCount={}",
                    assistantId,
                    omittedCount);
        }
        return Collections.unmodifiableSet(selected);
    }

    private String composeInstructions(
            PersonaSnapshot persona,
            Role role,
            List<KnowledgeSearchApi.KnowledgeBaseSummary> knowledgeBases) {
        var roleAppendix =
                new RoleAssignment(
                                role.key(),
                                role.name(),
                                role.responsibilities(),
                                role.nonResponsibilities())
                        .systemPromptAppendix();
        var knowledgeAppendix = buildKnowledgeAppendix(knowledgeBases);
        return """
                ## 助理人设
                名称：%s
                描述：%s
                性格：%s
                说话风格：%s

                %s

                %s

                %s
                """
                .formatted(
                        persona.name(),
                        persona.description(),
                        persona.personality(),
                        persona.speakingStyle(),
                        persona.instructions(),
                        roleAppendix,
                        knowledgeAppendix)
                .trim();
    }

    private String buildKnowledgeAppendix(
            List<KnowledgeSearchApi.KnowledgeBaseSummary> knowledgeBases) {
        if (knowledgeBases.isEmpty()) {
            return "";
        }
        var lines =
                knowledgeBases.stream()
                        .map(
                                kb -> {
                                    var description =
                                            kb.description() == null || kb.description().isBlank()
                                                    ? "（无描述）"
                                                    : kb.description();
                                    return " - %s：%s".formatted(kb.name(), description);
                                })
                        .toList();
        return "## 可用知识库\n" + String.join("\n", lines);
    }
}
