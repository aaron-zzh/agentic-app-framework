package com.xuejiai.aaf.module.intelligent.agent;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 创建 Agent Request DTO。 */
@Schema(description = "创建 Agent 请求")
public record AgentCreateDTO(
        @Schema(description = "Agent 标识", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String agentId,
        @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String name,
        @Schema(description = "描述") String description,
        @Schema(description = "系统提示词") String systemPrompt,
        @Schema(description = "模型 ID") String modelId,
        @Schema(description = "能力声明（JSON 数组）") String capabilities,
        @Schema(description = "工具列表（JSON 数组）") String tools,
        @Schema(description = "最大迭代次数", example = "10") Integer maxIterations,
        @Schema(description = "超时时间（秒）", example = "120") Integer timeoutSeconds) {}
