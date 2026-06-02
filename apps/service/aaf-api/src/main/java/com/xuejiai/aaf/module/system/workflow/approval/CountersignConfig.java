package com.xuejiai.aaf.module.system.workflow.approval;

import java.util.List;

/**
 * 会签/或签配置。
 *
 * @param mode 会签模式
 * @param passRatio 通过率阈值（RATIO 模式使用，0-100）
 * @param assignees 审批人列表
 */
public record CountersignConfig(CountersignMode mode, Integer passRatio, List<String> assignees) {

    /** 会签模式 */
    public enum CountersignMode {
        /** 全部通过 */
        ALL_APPROVE,
        /** 任一通过 */
        ANY_APPROVE,
        /** 按比例通过 */
        RATIO
    }
}
