package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;

import com.xuejiai.aaf.framework.intelligent.assistant.model.Role;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;

/** 按当前任务命中的 Skill 做渐进披露。 */
public interface EffectiveSkillResolver {

    /**
     * 解析当前有效 Role 中命中的单一 Skill。
     *
     * @param effectiveRole 前注意选出的任务 Role
     * @param skillKey 前注意选出的 Skill key
     * @return 命中的不可变技能列表；目录中不存在时为空
     */
    List<SkillDef> resolve(Role effectiveRole, String skillKey);
}
