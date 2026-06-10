package com.xuejiai.aaf.module.ai.agent.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Agent 信息响应 VO。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "Agent 信息")
public record AgentVO(
        @Schema(description = "编号") Long id,
        @Schema(description = "显示名称") String name,
        @Schema(description = "描述") String description,
        @Schema(description = "系统提示词") String systemPrompt,
        @Schema(description = "使用的模型 ID") Long modelId,
        @Schema(description = "能力声明（JSON 数组）") String capabilities,
        @Schema(description = "绑定的工具列表（JSON 数组）") String tools,
        @Schema(description = "预授权工具白名单（JSON 数组）") String allowedTools,
        @Schema(description = "MCP 服务器 URL 列表（JSON 数组）") String mcpServers,
        @Schema(description = "最大迭代次数") Integer maxIterations,
        @Schema(description = "超时时间（秒）") Integer timeoutSeconds,
        @Schema(description = "状态") String status,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
