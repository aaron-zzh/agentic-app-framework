package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillActivationMode;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillSelectionMode;

/** 仅包含选择阶段可见摘要的 Skill 清单。 */
public record SkillSelectionManifest(
        String roleKey,
        String defaultSkillKey,
        SkillSelectionMode selectionMode,
        int maxActivatedSkills,
        List<AuthorizedSkillSummary> candidates) {

    public SkillSelectionManifest {
        roleKey = requireText(roleKey, "roleKey");
        defaultSkillKey = normalize(defaultSkillKey);
        Objects.requireNonNull(selectionMode, "selectionMode 不能为空");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates 不能为空"));
        var candidateKeys = candidates.stream().map(AuthorizedSkillSummary::code).toList();
        if (candidateKeys.size() != Set.copyOf(candidateKeys).size()) {
            throw new IllegalArgumentException("candidates 不能包含重复 Skill");
        }
        if (candidates.stream()
                .anyMatch(
                        candidate -> candidate.activationMode() != SkillActivationMode.ON_DEMAND)) {
            throw new IllegalArgumentException("SkillSelection candidates 只能包含 ON_DEMAND Skill");
        }
        if (selectionMode == SkillSelectionMode.FIXED) {
            if (candidates.size() != 1
                    || maxActivatedSkills != 1
                    || defaultSkillKey == null
                    || !candidateKeys.contains(defaultSkillKey)) {
                throw new IllegalArgumentException("FIXED 必须精确绑定一个 ON_DEMAND Skill");
            }
        } else {
            if (defaultSkillKey != null) {
                throw new IllegalArgumentException("非 FIXED 选择不得配置默认 Skill");
            }
            if (maxActivatedSkills != candidates.size()) {
                throw new IllegalArgumentException("ON_DEMAND 激活上限必须等于候选总数");
            }
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value.trim();
    }
}
