package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;

/** 按 Assistant 已授权的 Role Skill 与 Assistant Skill 候选并集读取已发布执行定义。 */
public interface EffectiveSkillResolver {

    List<SkillDef> resolve(Set<String> authorizedSkillKeys, Set<String> requestedSkillKeys);
}
