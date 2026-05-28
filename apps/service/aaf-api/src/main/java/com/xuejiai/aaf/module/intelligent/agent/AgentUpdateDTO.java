package com.xuejiai.aaf.module.intelligent.agent;

import io.swagger.v3.oas.annotations.media.Schema;

/** 更新 Agent Request DTO（null 字段不修改）。 */
@Schema(description = "更新 Agent 请求")
public record AgentUpdateDTO(
        @Schema(description = "名称") String name,
        @Schema(description = "描述") String description,
        @Schema(description = "系统提示词") String systemPrompt,
        @Schema(description = "模型 ID") String modelId,
        @Schema(description = "能力声明（JSON 数组）") String capabilities,
        @Schema(description = "工具列表（JSON 数组）") String tools,
        @Schema(description = "最大迭代次数") Integer maxIterations,
        @Schema(description = "超时时间（秒）") Integer timeoutSeconds) {}
