package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Role;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 在 Skill 选择前确定本次执行使用的 Assistant Role。 */
public interface RoleSelector {

    RoleSelection select(RoleSelectionRequest request);

    record RoleSelectionRequest(
            AssistantDefinition definition,
            String taskInput,
            String preferredSkillKey,
            UserId userId) {

        public RoleSelectionRequest {
            Objects.requireNonNull(definition, "definition 不能为空");
            taskInput = taskInput == null ? "" : taskInput;
            preferredSkillKey = normalize(preferredSkillKey);
            Objects.requireNonNull(userId, "userId 不能为空");
        }

        private static String normalize(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }

    record RoleSelection(Role role, String selectedBy, String reason) {

        public RoleSelection {
            Objects.requireNonNull(role, "role 不能为空");
            selectedBy = requireText(selectedBy, "selectedBy");
            reason = requireText(reason, "reason");
        }

        private static String requireText(String value, String field) {
            Objects.requireNonNull(value, field + " 不能为空");
            if (value.isBlank()) {
                throw new IllegalArgumentException(field + " 不能为空白");
            }
            return value.trim();
        }
    }
}
