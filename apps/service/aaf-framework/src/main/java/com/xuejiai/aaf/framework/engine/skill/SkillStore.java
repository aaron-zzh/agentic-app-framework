package com.xuejiai.aaf.framework.engine.skill;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Skill 目录读取契约；仅暴露当前已发布的不可变执行版本。 */
public interface SkillStore {

    /** 按稳定业务码查询当前已发布版本。 */
    Optional<SkillRecord> findByCode(String skillCode);

    /** 按根对象 ID 查询当前已发布版本。 */
    Optional<SkillRecord> findBySkillId(Long skillId);

    /** 查询可执行的系统内置 Skill。 */
    List<SkillRecord> findBuiltIn();

    /** 根对象与当前可执行版本的只读投影。 */
    record SkillRecord(
            Long skillId,
            String code,
            String name,
            String summary,
            Long versionId,
            int version,
            String content,
            Set<String> requiredToolNames,
            Set<String> requiredModelCapabilities,
            boolean builtIn) {}
}
