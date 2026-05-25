package com.xuejiai.aaf.framework.engine.tool;

/**
 * 工具风险等级——决定权限申请策略。
 */
public enum ToolRiskLevel {
    /** 无风险（纯查询/计算）→ 无需权限检查 */
    NONE,
    /** 低风险（读取外部数据）→ 自动授权 + 记录日志 */
    LOW,
    /** 中风险（写入数据/调用外部服务）→ 首次需用户确认，后续自动 */
    MEDIUM,
    /** 高风险（删除/支付/敏感操作）→ 每次需用户确认 */
    HIGH,
    /** 极高风险（不可逆操作）→ 需管理员审批 */
    CRITICAL
}
