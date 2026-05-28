package com.xuejiai.aaf.module.system.workflow.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 流程定义视图对象
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "流程定义")
public record ProcessDefinitionVO(
        @Schema(description = "流程 Key") String processKey,
        @Schema(description = "流程名称") String name,
        @Schema(description = "版本号") int version) {}
