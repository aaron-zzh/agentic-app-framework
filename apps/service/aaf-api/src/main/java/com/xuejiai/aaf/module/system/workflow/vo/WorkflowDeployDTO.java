package com.xuejiai.aaf.module.system.workflow.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 部署流程定义请求
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "部署流程定义请求")
public record WorkflowDeployDTO(
        @NotBlank(message = "流程名称不能为空") @Schema(description = "流程名称") String name,
        @NotBlank(message = "BPMN XML 不能为空") @Schema(description = "BPMN XML 内容") String bpmnXml) {}
