package com.xuejiai.aaf.module.ai.agent.vo;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Agent 执行记录响应 VO。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "Agent 执行记录")
public record AgentExecutionVO(
        @Schema(description = "编号") Long id,
        @Schema(description = "执行 ID") String executionId,
        @Schema(description = "Agent ID") String agentId,
        @Schema(description = "Agent 名称") String agentName,
        @Schema(description = "用户 ID") Long userId,
        @Schema(description = "会话 ID") String conversationId,
        @Schema(description = "输入") String input,
        @Schema(description = "输出") String output,
        @Schema(description = "状态") String status,
        @Schema(description = "错误信息") String errorMessage,
        @Schema(description = "输入 Token 数") int tokenInput,
        @Schema(description = "输出 Token 数") int tokenOutput,
        @Schema(description = "开始时间") Instant startedAt,
        @Schema(description = "结束时间") Instant finishedAt,
        @Schema(description = "耗时（毫秒）") Long durationMs) {}
