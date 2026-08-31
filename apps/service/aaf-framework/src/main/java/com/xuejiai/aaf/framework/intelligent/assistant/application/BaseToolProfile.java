package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.Set;

/**
 * 版本化基础只读工具清单。
 *
 * <p>候选来源：{@code ai_tool_catalog} 中 {@code risk_level=LOW} 且 {@code read_only=TRUE}
 * 的工具，逐一核实无副作用、参数受控后收录。不等同于"所有通用工具"，网络/脚本类工具即便标记 {@code read_only} 也不自动进入（如 {@code
 * script.execute.javascript} 因 {@code risk_level=HIGH} 排除）。
 *
 * <p>该清单对所有 Skill、Role 组合恒定可见，不受 {@code RESTRICT} 收窄，也不需要 Skill 显式声明。 新增/移除工具需递增 {@link #VERSION}
 * 并同步 {@code action-governance.md} 实现态表。
 */
public final class BaseToolProfile {

    /** 清单版本号，变更时递增，供审计与执行画像回溯。 */
    public static final int VERSION = 1;

    private static final Set<String> TOOL_NAMES =
            Set.of("listBusinessActions", "list_workflows", "recognizeOcr", "queryWeather");

    private BaseToolProfile() {}

    /** 返回基础工具名清单（不可变副本）。 */
    public static Set<String> toolNames() {
        return TOOL_NAMES;
    }
}
