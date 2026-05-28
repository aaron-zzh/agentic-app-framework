package com.xuejiai.aaf.framework.engine.metadata;

/**
 * 元数据引擎——四类元数据（模块/插件/工具/组件）统一注册与管理。
 *
 * <p>核心职责：
 *
 * <ul>
 *   <li>统一注册表：工具、Agent、组件、插件的元数据注册与发现
 *   <li>语义漂移检测：工具实际行为 vs 文档描述的一致性校验
 *   <li>规范变更触发链：docs/ 变更 → 元数据同步 → 能力刷新
 * </ul>
 *
 * <p>从元引擎独立的理由：元数据是横切注册表，工具/Agent/组件/插件都注册在这里， 不只服务于元引擎。元引擎通过查询本引擎获取可用能力列表。
 *
 * @see com.xuejiai.aaf.framework.engine.tool.ToolRegistry 工具注册（待迁入）
 */
public interface MetadataEngine {

    /**
     * 检测语义漂移——对比工具实际行为与文档描述的一致性。
     *
     * @param toolId 工具标识
     * @return 是否存在漂移
     */
    boolean detectDrift(String toolId);
}
