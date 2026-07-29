package com.xuejiai.aaf.module.ai.agent.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 创建预定义智能体模板请求。 */
@Schema(description = "创建预定义智能体模板请求")
public record AgentDefinitionCreateDTO(
        @Schema(description = "稳定 Agent 标识", example = "expert.code-reviewer")
                @NotBlank
                @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")
                String agentId,
        @Schema(description = "显示名称") @NotBlank @Size(max = 128) String name,
        @Schema(description = "描述") @Size(max = 512) String description,
        @Schema(description = "系统提示词") @NotBlank String systemPrompt,
        @Schema(description = "模型数据库 ID") @NotNull @Positive Long modelId,
        @Schema(description = "能力声明") List<@NotBlank String> capabilities,
        @Schema(description = "可注册工具") List<@NotBlank String> tools,
        @Schema(description = "Agent 级工具白名单") List<@NotBlank String> allowedTools,
        @Schema(description = "MCP 服务器地址") List<@NotBlank String> mcpServers,
        @Schema(description = "最大迭代次数，默认 10") @Positive Integer maxIterations,
        @Schema(description = "超时时间（秒），默认 120") @Positive Integer timeoutSeconds) {}
