package com.xuejiai.aaf.module.system.workflow.vo;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 发送消息事件请求
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "发送消息事件请求")
public record WorkflowMessageDTO(
        @NotBlank(message = "消息名称不能为空") @Schema(description = "消息名称") String messageName,
        @NotBlank(message = "流程实例 ID 不能为空") @Schema(description = "目标流程实例 ID")
                String processInstanceId,
        @Schema(description = "变量") Map<String, Object> variables) {}
