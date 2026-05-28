package com.xuejiai.aaf.module.system.workflow.vo;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 发送信号事件请求
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "发送信号事件请求")
public record WorkflowSignalDTO(
        @NotBlank(message = "信号名称不能为空") @Schema(description = "信号名称") String signalName,
        @Schema(description = "变量") Map<String, Object> variables) {}
