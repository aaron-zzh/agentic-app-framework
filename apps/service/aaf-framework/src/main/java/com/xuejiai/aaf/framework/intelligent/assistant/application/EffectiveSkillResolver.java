package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;

import com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizedSkillSummary;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;

/** 按 Assistant 已冻结候选版本读取不可变 Skill 执行定义。 */
public interface EffectiveSkillResolver {

    List<SkillDef> resolve(List<AuthorizedSkillSummary> selectedCandidates);
}
