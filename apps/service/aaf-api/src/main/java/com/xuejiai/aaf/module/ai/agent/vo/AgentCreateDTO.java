package com.xuejiai.aaf.module.ai.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Agent 创建请求 DTO。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建 Agent 请求")
public record AgentCreateDTO(
        @Schema(description = "Agent 唯一标识", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
                String agentId,
        @Schema(description = "显示名称", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
                String name,
        @Schema(description = "描述") String description,
        @Schema(description = "系统提示词") String systemPrompt,
        @Schema(description = "使用的模型 ID") String modelId,
        @Schema(description = "能力声明（JSON 数组）") String capabilities,
        @Schema(description = "绑定的工具列表（JSON 数组）") String tools,
        @Schema(description = "预授权工具白名单（JSON 数组）") String allowedTools,
        @Schema(description = "MCP 服务器 URL 列表（JSON 数组）") String mcpServers,
        @Schema(description = "最大迭代次数") Integer maxIterations,
        @Schema(description = "超时时间（秒）") Integer timeoutSeconds) {}
