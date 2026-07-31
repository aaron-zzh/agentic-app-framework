package com.xuejiai.aaf.module.approval.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 审批流程实例视图对象。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "流程实例")
public record ApprovalProcessInstanceVO(
        @Schema(description = "流程实例 ID") String processInstanceId,
        @Schema(description = "流程 Key") String processKey,
        @Schema(description = "业务 Key") String businessKey,
        @Schema(description = "状态（running/suspended/completed/terminated）") String status,
        @Schema(description = "开始时间戳") long startTimeMs,
        @Schema(description = "结束时间戳（未结束为 null）") Long endTimeMs) {}
