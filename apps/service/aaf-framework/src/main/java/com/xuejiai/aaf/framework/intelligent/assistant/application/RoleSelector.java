package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Role;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 在当前已发布 Assistant 授权 Role 摘要内选择候选；Assistant 负责最终校验与冻结。 */
public interface RoleSelector {

    RoleSelection select(RoleSelectionRequest request);

    record RoleSelectionRequest(
            AssistantDefinition definition,
            Set<String> systemOnDemandSkillKeys,
            String taskInput,
            String preferredSkillKey,
            UserId userId) {

        public RoleSelectionRequest {
            Objects.requireNonNull(definition, "definition 不能为空");
            systemOnDemandSkillKeys =
                    Set.copyOf(
                            Objects.requireNonNull(
                                    systemOnDemandSkillKeys, "systemOnDemandSkillKeys 不能为空"));
            taskInput = taskInput == null ? "" : taskInput;
            preferredSkillKey = normalize(preferredSkillKey);
            Objects.requireNonNull(userId, "userId 不能为空");
        }

        /** 可直接交给选择器或未来 Coordinator 的受限候选提议，不暴露全局 Role。 */
        public RoleCandidateProposal candidateProposal() {
            if (definition.lifecycle() != AssistantDefinition.Lifecycle.PUBLISHED) {
                throw new IllegalStateException("只能为已发布 Assistant 生成 Role 候选提议");
            }
            return new RoleCandidateProposal(
                    definition.assistantId().value(),
                    definition.version().value(),
                    definition.roles().stream()
                            .map(
                                    role ->
                                            new AuthorizedRoleSummary(
                                                    role.key(),
                                                    role.name(),
                                                    role.responsibilities(),
                                                    role.nonResponsibilities(),
                                                    role.onDemandSkillKeys().stream()
                                                            .sorted()
                                                            .toList()))
                            .toList());
        }

        private static String normalize(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }

    record RoleCandidateProposal(
            String assistantId, long assistantRevision, List<AuthorizedRoleSummary> candidates) {
        public RoleCandidateProposal {
            assistantId = requireText(assistantId, "assistantId");
            if (assistantRevision < 0) {
                throw new IllegalArgumentException("assistantRevision 不能小于 0");
            }
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates 不能为空"));
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException("Role candidates 不能为空");
            }
        }
    }

    record AuthorizedRoleSummary(
            String key,
            String name,
            List<String> responsibilities,
            List<String> nonResponsibilities,
            List<String> authorizedSkillKeys) {
        public AuthorizedRoleSummary {
            key = requireText(key, "key");
            name = requireText(name, "name");
            responsibilities = List.copyOf(responsibilities);
            nonResponsibilities = List.copyOf(nonResponsibilities);
            authorizedSkillKeys = List.copyOf(authorizedSkillKeys);
        }
    }

    record RoleSelection(Role role, String selectedBy, String reason) {

        public RoleSelection {
            Objects.requireNonNull(role, "role 不能为空");
            selectedBy = requireText(selectedBy, "selectedBy");
            reason = requireText(reason, "reason");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value.trim();
    }
}
