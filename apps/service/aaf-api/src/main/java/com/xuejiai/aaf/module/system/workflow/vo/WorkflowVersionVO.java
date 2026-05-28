package com.xuejiai.aaf.module.system.workflow.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 工作流版本信息
 *
 * @param processKey 流程定义 key
 * @param version 版本号
 * @param name 版本名称
 * @param processDefinitionId 流程定义 ID
 * @param active 是否激活
 * @param description 版本说明
 */
@Schema(description = "工作流版本信息")
public record WorkflowVersionVO(
        @Schema(description = "流程定义 key") String processKey,
        @Schema(description = "版本号") int version,
        @Schema(description = "版本名称") String name,
        @Schema(description = "流程定义 ID") String processDefinitionId,
        @Schema(description = "是否激活") boolean active,
        @Schema(description = "版本说明") String description) {}
