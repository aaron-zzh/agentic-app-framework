package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;

/** 解析角色业务边界与 Agent 技术边界共同允许的工具。 */
public interface EffectiveToolResolver {

    /**
     * 计算全部角色工具白名单与 Agent 允许工具的交集。
     *
     * @param roleAllowedToolNames 助理全部角色允许工具名称的合并结果；空集表示角色层未限制
     * @param agentAllowedTools Agent 执行环境允许的工具
     * @return 两层共同允许的工具
     */
    List<ToolRef> resolve(Set<String> roleAllowedToolNames, List<ToolRef> agentAllowedTools);
}
