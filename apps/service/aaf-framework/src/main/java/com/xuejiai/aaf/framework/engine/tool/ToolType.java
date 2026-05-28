package com.xuejiai.aaf.framework.engine.tool;

/** 工具类型——决定执行方式。 */
public enum ToolType {
    /** 本地 Java 方法（@Tool 注解） */
    FUNCTION,
    /** MCP Server 远程工具 */
    MCP,
    /** HTTP 回调（Webhook） */
    HTTP,
    /** 用户自定义脚本（沙箱执行） */
    SCRIPT,
    /** 触发工作流 */
    WORKFLOW,
    /** 委托给另一个 Agent */
    AGENT
}
