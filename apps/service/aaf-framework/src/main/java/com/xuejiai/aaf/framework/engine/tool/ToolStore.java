package com.xuejiai.aaf.framework.engine.tool;

import java.util.List;
import java.util.Optional;

/**
 * 工具数据存储契约——引擎层通过此接口获取工具元数据。
 *
 * <p>工具来源：
 *
 * <ul>
 *   <li>LOCAL — Spring Bean 自动发现（@Tool 注解）
 *   <li>MCP — MCP Server 提供的远程工具
 *   <li>CUSTOM — 用户自定义（脚本/HTTP 回调）
 * </ul>
 */
public interface ToolStore {

    /** 查询所有已注册工具。 */
    List<ToolRecord> findAll();

    /** 按名称查询。 */
    Optional<ToolRecord> findByName(String name);

    /** 按来源查询。 */
    List<ToolRecord> findBySource(String source);

    /** 获取指定 Role 的工具白名单。 */
    List<String> getWhitelistByRole(String roleId);

    /** 工具元数据记录 */
    record ToolRecord(
            String name,
            String description,
            String source,
            String parametersSchema,
            boolean enabled) {}
}
