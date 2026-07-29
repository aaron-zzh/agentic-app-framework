package com.xuejiai.aaf.module.ai.agent.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 更新预定义智能体模板请求；稳定 agentId 不允许修改。 */
@Schema(description = "更新预定义智能体模板请求")
public record AgentDefinitionUpdateDTO(
        @Schema(description = "显示名称") @NotBlank @Size(max = 128) String name,
        @Schema(description = "描述") @Size(max = 512) String description,
        @Schema(description = "系统提示词") @NotBlank String systemPrompt,
        @Schema(description = "模型数据库 ID") @NotNull @Positive Long modelId,
        @Schema(description = "能力声明") @NotNull List<@NotBlank String> capabilities,
        @Schema(description = "可注册工具") @NotNull List<@NotBlank String> tools,
        @Schema(description = "Agent 级工具白名单") @NotNull List<@NotBlank String> allowedTools,
        @Schema(description = "MCP 服务器地址") @NotNull List<@NotBlank String> mcpServers,
        @Schema(description = "最大迭代次数") @NotNull @Positive Integer maxIterations,
        @Schema(description = "超时时间（秒）") @NotNull @Positive Integer timeoutSeconds) {}
