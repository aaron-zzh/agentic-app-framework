package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.model.SkillSelectionManifest;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 在正式 Agent 执行前、仅依据候选摘要选择待激活 Skill 的无副作用端口。 */
public interface SkillSelectionPort {

    SkillSelectionDecision select(SelectionRequest request);

    record SelectionRequest(
            SkillSelectionManifest manifest,
            String taskInput,
            String preferredSkillKey,
            UserId userId) {

        public SelectionRequest {
            Objects.requireNonNull(manifest, "manifest 不能为空");
            taskInput = taskInput == null ? "" : taskInput;
            preferredSkillKey =
                    preferredSkillKey == null || preferredSkillKey.isBlank()
                            ? null
                            : preferredSkillKey.trim();
        }
    }

    record SkillSelectionDecision(
            List<String> selectedSkillKeys, String selectedBy, String reason) {

        public SkillSelectionDecision {
            selectedSkillKeys =
                    List.copyOf(
                            Objects.requireNonNull(selectedSkillKeys, "selectedSkillKeys 不能为空"));
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
