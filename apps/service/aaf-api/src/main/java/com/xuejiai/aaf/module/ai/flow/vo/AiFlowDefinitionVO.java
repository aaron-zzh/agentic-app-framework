package com.xuejiai.aaf.module.ai.flow.vo;

import java.time.LocalDateTime;

import lombok.Data;

/** AI 工作流定义响应 VO */
@Data
public class AiFlowDefinitionVO {
    private Long id;
    private String name;
    private String description;
    private String mode;
    private String definition;
    private String status;
    private String deploymentId;
    private LocalDateTime publishedAt;
    private Boolean agentCallable;
    private Boolean requireConfirm;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
