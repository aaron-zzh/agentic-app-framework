package com.xuejiai.aaf.module.ai.flow.vo;

import lombok.Data;

/** 更新 AI 工作流定义请求 */
@Data
public class AiFlowDefinitionUpdateDTO {
    private String name;
    private String description;
    private String mode;
    private String definition;
    private Boolean agentCallable;
    private Boolean requireConfirm;
}
