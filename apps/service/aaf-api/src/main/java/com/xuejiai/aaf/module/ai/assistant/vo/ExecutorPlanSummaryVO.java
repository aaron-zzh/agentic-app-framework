package com.xuejiai.aaf.module.ai.assistant.vo;

import java.util.List;

/**
 * ExecutorPlan 只读投影摘要（AAF-114 #11409 任务摘要组件）。
 *
 * <p>只携带 canonical {@code planId} 与 {@code revision}，前端据此从权威服务端投影读取实时状态， 不在客户端建立独立的计划生命周期状态。
 */
public record ExecutorPlanSummaryVO(
        String planId,
        String subTaskId,
        int revision,
        String status,
        String goal,
        List<StepSummary> steps) {

    /** 单个步骤的只读摘要；{@code ordinal} 仅用于展示排序。 */
    public record StepSummary(
            String stepKey, int ordinal, String title, String status, String failureCode) {}
}
