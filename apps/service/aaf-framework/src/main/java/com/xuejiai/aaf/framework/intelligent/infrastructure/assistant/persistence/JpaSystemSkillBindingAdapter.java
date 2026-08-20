package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.engine.skill.SkillStore;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillActivationMode;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillBinding;
import com.xuejiai.aaf.framework.intelligent.assistant.port.SystemSkillBindingPort;

/** 系统级 Skill 绑定持久化适配器。 */
public final class JpaSystemSkillBindingAdapter implements SystemSkillBindingPort {

    private final SystemSkillBindingRepository repository;
    private final SkillStore skills;

    public JpaSystemSkillBindingAdapter(
            SystemSkillBindingRepository repository, SkillStore skills) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        this.skills = Objects.requireNonNull(skills, "skills 不能为空");
    }

    @Override
    public List<SkillBinding> findEnabled() {
        var bindings =
                repository.findByEnabledTrueAndDeletedFalseOrderBySortOrderAscIdAsc().stream()
                        .map(
                                entity -> {
                                    var skill =
                                            skills.findSummaryBySkillId(entity.getSkillId())
                                                    .orElseThrow(
                                                            () ->
                                                                    new IllegalStateException(
                                                                            "SYSTEM Skill 不存在可执行版本: "
                                                                                    + entity
                                                                                            .getSkillId()));
                                    if (!skill.requiredToolNames().isEmpty()) {
                                        throw new IllegalStateException(
                                                "SYSTEM Skill 初版禁止声明工具要求: " + skill.code());
                                    }
                                    return new SkillBinding(
                                            skill.code(),
                                            SkillActivationMode.valueOf(
                                                    entity.getActivationMode()));
                                })
                        .toList();
        return SkillBinding.copyOf(bindings, "systemSkillBindings");
    }
}
