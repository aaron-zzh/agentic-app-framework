package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillSelectionMode;

/** Assistant 在选定 Role 后计算的受限 Skill 候选清单。 */
public record SkillSelectionManifest(
        String roleKey,
        String defaultSkillKey,
        SkillSelectionMode selectionMode,
        int maxActivatedSkills,
        List<AuthorizedSkillSummary> candidates) {

    public SkillSelectionManifest {
        roleKey = requireText(roleKey, "roleKey");
        defaultSkillKey = requireText(defaultSkillKey, "defaultSkillKey");
        Objects.requireNonNull(selectionMode, "selectionMode 不能为空");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates 不能为空"));
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates 不能为空");
        }
        if (maxActivatedSkills < 1 || maxActivatedSkills > candidates.size()) {
            throw new IllegalArgumentException("maxActivatedSkills 超出候选范围");
        }
        if (candidates.stream().noneMatch(candidate -> candidate.code().equals(defaultSkillKey))) {
            throw new IllegalArgumentException("defaultSkillKey 不在候选范围内");
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
