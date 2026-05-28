package com.xuejiai.aaf.module.aigc.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.module.aigc.enums.BatchTaskStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/** 批量生成任务 Response VO。 */
public record BatchGenerationTaskVO(
        @Schema(description = "任务 ID", example = "1") Long id,
        @Schema(description = "任务状态", example = "RUNNING") BatchTaskStatus status,
        @Schema(description = "总生成数量", example = "10") Integer totalCount,
        @Schema(description = "已完成数量", example = "5") Integer completedCount,
        @Schema(description = "失败数量", example = "1") Integer failedCount,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
