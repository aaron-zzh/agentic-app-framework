package com.xuejiai.aaf.module.ai.aigc.enums;

/**
 * 批量生成任务状态。
 *
 * @author AaronZZH & Kiro
 */
public enum BatchTaskStatus {
    /** 等待执行 */
    PENDING,
    /** 执行中 */
    RUNNING,
    /** 已完成 */
    COMPLETED,
    /** 失败 */
    FAILED,
    /** 已取消 */
    CANCELLED
}
