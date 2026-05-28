package com.xuejiai.aaf.module.system.workflow.vo;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 流程状态响应。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "流程状态")
public record WorkflowStatusVO(
        @Schema(description = "流程实例ID") String processInstanceId,
        @Schema(description = "实体类型") String entityType,
        @Schema(description = "实体ID") Long entityId,
        @Schema(description = "发起人") String initiator,
        @Schema(description = "是否已结束") boolean ended,
        @Schema(description = "当前待办任务ID") String currentTaskId,
        @Schema(description = "当前审批人") String currentAssignee,
        @Schema(description = "审批历史") List<HistoryItem> history) {

    /** 审批历史条目。 */
    @Schema(description = "审批历史条目")
    public record HistoryItem(
            @Schema(description = "任务名称") String taskName,
            @Schema(description = "审批人") String assignee,
            @Schema(description = "操作") String action,
            @Schema(description = "意见") String comment,
            @Schema(description = "完成时间") LocalDateTime completedTime) {}
}
