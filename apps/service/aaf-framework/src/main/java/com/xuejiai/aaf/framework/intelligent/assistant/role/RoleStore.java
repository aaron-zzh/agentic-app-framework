package com.xuejiai.aaf.framework.intelligent.assistant.role;

import java.util.List;

/** Role 数据存储契约——获取 Role 关联的技能和工具配置。 */
public interface RoleStore {

    /** 获取 Role 关联的 skillId 列表。 */
    List<Long> getSkillIds(Long roleId);

    /** 获取 Role 关联的工具授权池（角色级工具白名单）。 */
    List<String> getToolWhitelist(Long roleId);

    /** 获取助理挂载的角色 ID 列表（经 ai_assistant_role 关联，按排序值升序）。 */
    List<Long> getRoleIdsByAssistant(Long assistantId);
}
