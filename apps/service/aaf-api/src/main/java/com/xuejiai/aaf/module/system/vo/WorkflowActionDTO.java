package com.xuejiai.aaf.module.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 审批操作请求。 */
@Schema(description = "审批操作请求")
public record WorkflowActionDTO(
        @NotBlank @Schema(description = "任务ID") String taskId,
        @Schema(description = "审批意见") String comment) {}
