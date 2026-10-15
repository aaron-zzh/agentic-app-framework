package com.xuejiai.aaf.module.ai.skill;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.engine.skill.SkillDefinition;

import lombok.RequiredArgsConstructor;

/**
 * Skill 访问性查询：供 Role 引用校验与运行时再校验判断技能对用户是否可引用、可用。
 *
 * <p>只暴露访问判断所需的最小信息，不向 {@code module.ai.role} 暴露 Skill 实体与仓储。
 */
@Component
@RequiredArgsConstructor
public class SkillAccessQuery {

    private static final String APPROVED = "APPROVED";
    private static final String PUBLIC = "PUBLIC";
    private static final String INTERNAL = "INTERNAL";
    private static final String PRIVATE = "PRIVATE";

    private final SkillDefinitionRepository repository;
    private final SkillVersionRepository versionRepository;

    /**
     * 技能访问信息。
     *
     * @param code 技能稳定业务键
     * @param visibility 技能可见性：PRIVATE、WORKSPACE、PUBLIC、INTERNAL
     * @param builtIn 是否系统内置
     * @param ownerId 技能所有者
     * @param published 是否有已批准的当前版本
     */
    public record SkillAccess(
            String code, String visibility, boolean builtIn, Long ownerId, boolean published) {

        /** 全员可引用：内置、PUBLIC 或 INTERNAL，且有已批准的当前版本。 */
        public boolean shared() {
            return published
                    && (builtIn || PUBLIC.equals(visibility) || INTERNAL.equals(visibility));
        }

        /** 是否为指定用户自己的私有技能。 */
        public boolean privateOf(Long userId) {
            return published
                    && PRIVATE.equals(visibility)
                    && userId != null
                    && userId.equals(ownerId);
        }

        /** 技能对用户是否可见可用：全员可引用，或用户自己的私有技能；WORKSPACE 技能一律不可用。 */
        public boolean visibleTo(Long userId) {
            return shared() || privateOf(userId);
        }
    }

    /** 批量读取技能访问信息，不存在的技能不在结果里。 */
    @Transactional(readOnly = true)
    public Map<String, SkillAccess> findByCodes(Collection<String> codes) {
        var result = new HashMap<String, SkillAccess>();
        if (codes == null || codes.isEmpty()) {
            return result;
        }
        for (var definition : repository.findByCodeIn(codes)) {
            result.put(definition.getCode(), toAccess(definition));
        }
        return result;
    }

    private SkillAccess toAccess(SkillDefinition definition) {
        var published =
                definition.getCurrentVersionId() != null
                        && versionRepository
                                .findByIdAndStatus(definition.getCurrentVersionId(), APPROVED)
                                .filter(version -> version.getSkillId().equals(definition.getId()))
                                .isPresent();
        return new SkillAccess(
                definition.getCode(),
                definition.getVisibility(),
                Boolean.TRUE.equals(definition.getBuiltIn()),
                definition.getOwnerId(),
                published);
    }
}
