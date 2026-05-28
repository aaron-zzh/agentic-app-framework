package com.xuejiai.aaf.module.intelligent.memory;

import io.swagger.v3.oas.annotations.media.Schema;

/** 记忆统计信息 Response VO。 */
@Schema(description = "记忆统计信息")
public record MemoryStatsVO(
        @Schema(description = "短期记忆数量") long shortTermCount,
        @Schema(description = "长期记忆数量") long longTermCount,
        @Schema(description = "情景记忆数量") long episodicCount,
        @Schema(description = "程序性记忆数量") long proceduralCount,
        @Schema(description = "总数") long totalCount) {}
