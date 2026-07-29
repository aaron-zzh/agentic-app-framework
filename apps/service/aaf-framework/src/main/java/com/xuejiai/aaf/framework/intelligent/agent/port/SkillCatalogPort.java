package com.xuejiai.aaf.framework.intelligent.agent.port;

import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;

/** 技能定义只读目录。 */
public interface SkillCatalogPort {

    Optional<SkillDef> findByCode(String skillCode);

    List<SkillDef> findBuiltIn();
}
