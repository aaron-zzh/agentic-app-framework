package com.xuejiai.aaf.module.system.workflow.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 任务转交请求。 */
@Schema(description = "任务转交")
public record WorkflowTransferDTO(
        @NotBlank @Schema(description = "Flowable 任务 ID") String taskId,
        @NotNull @Schema(description = "目标用户 ID") Long targetUserId) {}
