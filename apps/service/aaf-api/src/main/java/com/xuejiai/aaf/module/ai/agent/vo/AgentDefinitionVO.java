package com.xuejiai.aaf.module.ai.agent.vo;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 预定义智能体模板响应。 */
@Schema(description = "预定义智能体模板")
public record AgentDefinitionVO(
        @Schema(description = "数据库 ID") Long id,
        @Schema(description = "稳定 Agent 标识") String agentId,
        @Schema(description = "定义版本") Integer version,
        @Schema(description = "显示名称") String name,
        @Schema(description = "描述") String description,
        @Schema(description = "系统提示词") String systemPrompt,
        @Schema(description = "模型数据库 ID") Long modelId,
        @Schema(description = "能力声明") List<String> capabilities,
        @Schema(description = "可注册工具") List<String> tools,
        @Schema(description = "Agent 级工具白名单") List<String> allowedTools,
        @Schema(description = "MCP 服务器地址") List<String> mcpServers,
        @Schema(description = "最大迭代次数") Integer maxIterations,
        @Schema(description = "超时时间（秒）") Integer timeoutSeconds,
        @Schema(description = "状态：active/inactive/archived") String status,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
