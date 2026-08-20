package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.agent.port.SkillCatalogPort;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;

/** Assistant 已授权候选并集内的 immutable APPROVED Skill 解析器。 */
public final class DefaultEffectiveSkillResolver implements EffectiveSkillResolver {

    private final SkillCatalogPort skillCatalog;

    public DefaultEffectiveSkillResolver(SkillCatalogPort skillCatalog) {
        this.skillCatalog = Objects.requireNonNull(skillCatalog, "skillCatalog 不能为空");
    }

    @Override
    public List<SkillDef> resolve(Set<String> authorizedSkillKeys, Set<String> requestedSkillKeys) {
        var authorized =
                Set.copyOf(Objects.requireNonNull(authorizedSkillKeys, "authorizedSkillKeys 不能为空"));
        var requested =
                Set.copyOf(Objects.requireNonNull(requestedSkillKeys, "requestedSkillKeys 不能为空"));
        if (!authorized.containsAll(requested)) {
            throw new IllegalArgumentException("Skill 不属于 Assistant 当前候选范围");
        }
        if (requested.isEmpty()) {
            return List.of();
        }
        return requested.stream()
                .sorted()
                .map(
                        skillKey ->
                                skillCatalog
                                        .findByCode(skillKey)
                                        .orElseThrow(
                                                () ->
                                                        new IllegalArgumentException(
                                                                "不存在 APPROVED Skill: " + skillKey)))
                .toList();
    }
}
