package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizedSkillSummary;
import com.xuejiai.aaf.framework.intelligent.agent.port.SkillCatalogPort;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;

/** Assistant 已授权候选并集内的 immutable APPROVED Skill 解析器。 */
public final class DefaultEffectiveSkillResolver implements EffectiveSkillResolver {

    private final SkillCatalogPort skillCatalog;

    public DefaultEffectiveSkillResolver(SkillCatalogPort skillCatalog) {
        this.skillCatalog = Objects.requireNonNull(skillCatalog, "skillCatalog 不能为空");
    }

    @Override
    public List<SkillDef> resolve(List<AuthorizedSkillSummary> selectedCandidates) {
        var candidates =
                List.copyOf(Objects.requireNonNull(selectedCandidates, "selectedCandidates 不能为空"));
        return candidates.stream()
                .sorted(Comparator.comparing(AuthorizedSkillSummary::code))
                .map(
                        candidate ->
                                skillCatalog
                                        .findByVersion(candidate.version())
                                        .filter(
                                                skill ->
                                                        skill.code().equals(candidate.code())
                                                                && skill.version()
                                                                        .equals(
                                                                                candidate
                                                                                        .version()))
                                        .orElseThrow(
                                                () ->
                                                        new IllegalArgumentException(
                                                                "不存在 APPROVED SkillVersion: "
                                                                        + candidate.code()
                                                                        + "@"
                                                                        + candidate.version())))
                .toList();
    }
}
