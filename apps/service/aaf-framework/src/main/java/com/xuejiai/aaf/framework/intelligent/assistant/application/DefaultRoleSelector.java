package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.core.llm.LlmClient;

/** Role 选择器：显式 Skill 确定性限界；AUTO 仅向模型暴露当前 Assistant Role 摘要。 */
public final class DefaultRoleSelector implements RoleSelector {

    private final LlmClient llmClient;

    public DefaultRoleSelector() {
        this.llmClient = null;
    }

    public DefaultRoleSelector(LlmClient llmClient) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient 不能为空");
    }

    @Override
    public RoleSelection select(RoleSelectionRequest request) {
        var proposal = request.candidateProposal();
        var preferredSkillKey = request.preferredSkillKey();
        if (preferredSkillKey != null) {
            return selectByExplicitSkill(request, preferredSkillKey);
        }
        if (llmClient != null) {
            var selected = selectByModel(request, proposal);
            if (selected != null) {
                return selected;
            }
        }
        return new RoleSelection(
                request.definition().defaultRole(),
                "DEFAULT_BINDING",
                "Role 选择模型不可用或未命中，使用 Assistant 默认 Role 绑定");
    }

    private static RoleSelection selectByExplicitSkill(
            RoleSelectionRequest request, String preferredSkillKey) {
        if (request.systemOnDemandSkillKeys().contains(preferredSkillKey)) {
            return new RoleSelection(
                    request.definition().defaultRole(),
                    "REQUEST",
                    "显式 SYSTEM ON_DEMAND Skill 使用默认 Role");
        }
        if (request.definition().assistantOnDemandSkillKeys().contains(preferredSkillKey)) {
            return new RoleSelection(
                    request.definition().defaultRole(),
                    "REQUEST",
                    "显式 Assistant ON_DEMAND Skill 使用默认 Role");
        }
        var matches =
                request.definition().roles().stream()
                        .filter(role -> role.onDemandSkillKeys().contains(preferredSkillKey))
                        .toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException(
                    "显式 Skill 只能引用当前 Scope 的 ON_DEMAND Skill: " + preferredSkillKey);
        }
        if (matches.size() > 1) {
            var defaultRole = request.definition().defaultRole();
            if (defaultRole.onDemandSkillKeys().contains(preferredSkillKey)) {
                return new RoleSelection(defaultRole, "REQUEST", "显式 Skill 命中默认 Role");
            }
            throw new IllegalStateException("显式 Skill 同时属于多个非默认 Role，无法唯一选择: " + preferredSkillKey);
        }
        return new RoleSelection(matches.getFirst(), "REQUEST", "显式 Skill 唯一命中 Role");
    }

    private RoleSelection selectByModel(
            RoleSelectionRequest request, RoleCandidateProposal proposal) {
        try {
            var response =
                    llmClient.call(
                            List.of(
                                    LlmClient.LlmMessage.system(systemPrompt(proposal)),
                                    LlmClient.LlmMessage.user(request.taskInput())),
                            "ROLE_SELECTION",
                            numericUserId(request.userId()));
            var root = JsonUtils.readTreeStrict(response);
            if (root == null || !root.isObject() || root.size() != 1 || !root.has("roleKey")) {
                return null;
            }
            var roleKey = root.get("roleKey");
            if (roleKey == null || !roleKey.isTextual()) {
                return null;
            }
            return request.definition().roles().stream()
                    .filter(role -> role.key().equals(roleKey.textValue()))
                    .findFirst()
                    .map(
                            role ->
                                    new RoleSelection(
                                            role,
                                            "SELECTION_MODEL",
                                            "无副作用模型在 Assistant 授权 Role 摘要内选择"))
                    .orElse(null);
        } catch (RuntimeException ignored) {
            // 非 JSON、契约外字段或模型异常一律回到 Assistant 默认绑定，不扩大候选范围。
            return null;
        }
    }

    private static String systemPrompt(RoleCandidateProposal proposal) {
        var candidates =
                proposal.candidates().stream()
                        .map(
                                candidate ->
                                        "- %s | %s | 职责=%s | 非职责=%s | 可用Skill=%s"
                                                .formatted(
                                                        candidate.key(),
                                                        candidate.name(),
                                                        candidate.responsibilities(),
                                                        candidate.nonResponsibilities(),
                                                        candidate.authorizedSkillKeys()))
                        .collect(java.util.stream.Collectors.joining("\n"));
        return """
                你是 AAF 的无副作用 Role 选择器。只能依据当前 Assistant 已发布候选摘要选择，不能访问全局 Role/Skill，不能调用工具。
                仅选择一个给定 roleKey，不得虚构。仅输出 JSON：{"roleKey":"role-key"}。

                Assistant：%s@%d
                候选摘要：
                %s
                """
                .formatted(proposal.assistantId(), proposal.assistantRevision(), candidates);
    }

    private static Long numericUserId(
            com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId userId) {
        try {
            var value = Long.parseLong(userId.value());
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
