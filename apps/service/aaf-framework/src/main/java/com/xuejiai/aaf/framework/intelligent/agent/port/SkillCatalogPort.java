package com.xuejiai.aaf.framework.intelligent.agent.port;

import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillSummary;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillVersionRef;

/** 技能定义只读目录。 */
public interface SkillCatalogPort {

    Optional<SkillSummary> findSummaryByCode(String skillCode);

    Optional<SkillDef> findByCode(String skillCode);

    Optional<SkillDef> findByVersion(SkillVersionRef version);

    List<SkillDef> findBuiltIn();
}
