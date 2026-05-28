package com.xuejiai.aaf.module.system.workflow.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 待办任务视图对象
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "待办任务")
public record WorkflowTaskVO(
        @Schema(description = "任务 ID") String taskId,
        @Schema(description = "流程实例 ID") String processInstanceId,
        @Schema(description = "任务名称") String name,
        @Schema(description = "审批人") String assignee) {}
