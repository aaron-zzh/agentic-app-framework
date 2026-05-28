package com.xuejiai.aaf.module.intelligent.agent;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** Agent 信息 Response VO。 */
@Schema(description = "Agent 信息")
public record AgentVO(
        @Schema(description = "编号") Long id,
        @Schema(description = "Agent 标识") String agentId,
        @Schema(description = "名称") String name,
        @Schema(description = "描述") String description,
        @Schema(description = "系统提示词") String systemPrompt,
        @Schema(description = "模型 ID") String modelId,
        @Schema(description = "能力声明（JSON）") String capabilities,
        @Schema(description = "工具列表（JSON）") String tools,
        @Schema(description = "最大迭代次数") Integer maxIterations,
        @Schema(description = "超时时间（秒）") Integer timeoutSeconds,
        @Schema(description = "状态") String status,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
