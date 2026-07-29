package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;

import com.xuejiai.aaf.framework.intelligent.assistant.model.Role;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;

/** 合并 Assistant 角色技能与内置通用技能的领域服务。 */
public interface EffectiveSkillResolver {

    /**
     * 合并角色技能和内置通用技能，按技能标识去重，同标识时角色技能优先。
     *
     * @param assignedRoles Assistant 当前挂载的全部角色
     * @return 合并后的不可变技能列表
     */
    List<SkillDef> resolve(List<Role> assignedRoles);
}
