package com.xuejiai.aaf.module.system.workflow.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 工作流发布请求
 *
 * @param processKey 流程定义 key
 * @param name 发布名称
 * @param description 版本说明
 */
@Schema(description = "工作流发布请求")
public record WorkflowPublishDTO(
        @Schema(description = "流程定义 key") @NotBlank String processKey,
        @Schema(description = "发布名称") @NotBlank String name,
        @Schema(description = "版本说明") String description) {}
