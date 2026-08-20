package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;

import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillSelectionMode;

/** 模型选择不可用时的确定性 fail-closed 选择器。 */
public final class DefaultSkillSelectionPort implements SkillSelectionPort {

    @Override
    public SkillSelectionDecision select(SelectionRequest request) {
        var manifest = request.manifest();
        if (manifest.selectionMode() == SkillSelectionMode.FIXED) {
            return new SkillSelectionDecision(
                    List.of(manifest.defaultSkillKey()), "AAF_POLICY", "固定 Route 精确选择目标 Skill");
        }
        if (request.preferredSkillKey() != null
                && manifest.candidates().stream()
                        .anyMatch(
                                candidate ->
                                        candidate.code().equals(request.preferredSkillKey()))) {
            return new SkillSelectionDecision(
                    List.of(request.preferredSkillKey()), "USER_REQUEST", "用户显式请求候选 Scope 内 Skill");
        }
        return new SkillSelectionDecision(List.of(), "FAIL_CLOSED", "选择模型不可用，不激活 ON_DEMAND Skill");
    }
}
