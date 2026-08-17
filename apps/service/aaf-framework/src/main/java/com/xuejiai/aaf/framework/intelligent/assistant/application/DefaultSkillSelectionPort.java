package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;

import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillSelectionMode;

/** 模型选择不可用时的确定性选择器；只返回 manifest 内候选 code。 */
public final class DefaultSkillSelectionPort implements SkillSelectionPort {

    @Override
    public SkillSelectionDecision select(SelectionRequest request) {
        var manifest = request.manifest();
        if (manifest.selectionMode() == SkillSelectionMode.FIXED) {
            return new SkillSelectionDecision(
                    List.of(manifest.defaultSkillKey()), "AAF_POLICY", "固定 Route 仅允许默认 Skill");
        }
        if (request.preferredSkillKey() != null
                && manifest.candidates().stream()
                        .anyMatch(
                                candidate ->
                                        candidate.code().equals(request.preferredSkillKey()))) {
            return new SkillSelectionDecision(
                    List.of(request.preferredSkillKey()), "USER_REQUEST", "用户显式请求候选 Scope 内 Skill");
        }
        return new SkillSelectionDecision(
                List.of(manifest.defaultSkillKey()), "AAF_POLICY", "模型选择不可用，使用 Route 默认 Skill");
    }
}
