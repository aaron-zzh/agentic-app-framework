package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.util.List;

import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillBinding;

/** 读取当前启用的系统级 Skill 绑定。 */
public interface SystemSkillBindingPort {

    List<SkillBinding> findEnabled();
}
