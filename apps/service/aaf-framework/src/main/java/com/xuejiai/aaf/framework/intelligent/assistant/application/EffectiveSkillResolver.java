package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.assistant.model.Role;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;

/** 按当前 Role 边界读取已发布 Skill 的执行定义。 */
public interface EffectiveSkillResolver {

    List<SkillDef> resolve(Role effectiveRole, Set<String> skillKeys);
}
