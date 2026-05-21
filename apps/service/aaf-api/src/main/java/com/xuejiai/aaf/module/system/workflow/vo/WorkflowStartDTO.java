package com.xuejiai.aaf.module.system.workflow.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 启动流程请求。 */
@Schema(description = "启动流程请求")
public record WorkflowStartDTO(
        @NotBlank @Schema(description = "实体类型", example = "order") String entityType,
        @NotNull @Schema(description = "实体ID") Long entityId,
        @NotBlank @Schema(description = "审批人用户名") String assignee) {}
