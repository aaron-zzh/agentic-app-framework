package com.xuejiai.aaf.framework.intelligent.core.skill;

import java.util.List;
import java.util.Optional;

/**
 * 技能提供者接口——engine/skill 实现，intelligent/assistant 调用。
 */
public interface SkillProvider {

    /**
     * 根据用户输入匹配最合适的技能。
     *
     * @param assistantId 助理 ID
     * @param userInput   用户输入
     * @return 匹配到的技能，无匹配返回 empty
     */
    Optional<SkillDef> match(String assistantId, String userInput);

    /**
     * 获取助理的所有可用技能。
     */
    List<SkillDef> getDefinitions(String assistantId);
}
