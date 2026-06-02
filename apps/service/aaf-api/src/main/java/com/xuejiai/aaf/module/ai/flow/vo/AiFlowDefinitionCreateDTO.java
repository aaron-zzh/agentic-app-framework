package com.xuejiai.aaf.module.ai.flow.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 创建 AI 工作流定义请求 */
@Data
public class AiFlowDefinitionCreateDTO {

    @NotBlank private String name;

    private String description;

    @NotBlank private String mode;

    private String definition = "{}";

    private Boolean agentCallable = false;

    private Boolean requireConfirm = true;
}
