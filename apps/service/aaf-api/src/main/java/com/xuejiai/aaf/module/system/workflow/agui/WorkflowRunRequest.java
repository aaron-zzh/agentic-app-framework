package com.xuejiai.aaf.module.system.workflow.agui;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 工作流 AG-UI 运行请求
 *
 * @param processKey 流程定义 key
 * @param variables 流程变量
 * @param messages 对话消息（可选，用于对话式流程）
 */
@Schema(description = "工作流 AG-UI 运行请求")
public record WorkflowRunRequest(
        @Schema(description = "流程定义 key", example = "generic-approval") @NotBlank String processKey,
        @Schema(description = "流程变量") Map<String, Object> variables,
        @Schema(description = "对话消息列表（可选）") List<Message> messages) {

    /** 对话消息 */
    public record Message(
            @Schema(description = "角色", example = "user") String role,
            @Schema(description = "内容") String content) {}
}
