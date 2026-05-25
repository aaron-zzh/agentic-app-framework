package com.xuejiai.aaf.framework.intelligent.assistant.role;

import java.util.List;

/**
 * Role 数据存储契约——获取 Role 关联的技能和工具配置。
 */
public interface RoleStore {

    /** 获取 Role 关联的 skillId 列表。 */
    List<String> getSkillIds(String roleId);

    /** 获取 Role 关联的工具白名单。 */
    List<String> getToolWhitelist(String roleId);
}
