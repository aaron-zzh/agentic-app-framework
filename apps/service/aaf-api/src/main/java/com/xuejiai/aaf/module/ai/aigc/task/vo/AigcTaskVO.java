package com.xuejiai.aaf.module.ai.aigc.task.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AIGC 任务响应 VO。
 *
 * @author Kiro
 */
@Schema(description = "AIGC 任务")
public record AigcTaskVO(
        @Schema(description = "任务 ID") Long id,
        @Schema(description = "用户 ID") Long userId,
        @Schema(description = "任务类型：IMAGE / VIDEO / MODEL_3D") String type,
        @Schema(description = "任务状态：PENDING / RUNNING / SUCCESS / FAIL") String status,
        @Schema(description = "提供商") String provider,
        @Schema(description = "模型名称") String model,
        @Schema(description = "生成 prompt") String prompt,
        @Schema(description = "第三方任务 ID") String taskId,
        @Schema(description = "第三方结果 URL") String resultUrl,
        @Schema(description = "OSS 存储 URL") String ossUrl,
        @Schema(description = "失败原因") String errorMsg,
        @Schema(description = "所属项目 ID") Long projectId,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
