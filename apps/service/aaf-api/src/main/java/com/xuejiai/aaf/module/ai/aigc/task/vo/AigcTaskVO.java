package com.xuejiai.aaf.module.ai.aigc.task.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AIGC 任务响应 VO。
 *
 * @author AaronZZH
 */
@Schema(description = "AIGC 任务")
public record AigcTaskVO(
        @Schema(description = "任务 ID") Long id,
        @Schema(description = "用户 ID") Long userId,
        @Schema(description = "任务类型：IMAGE / VIDEO / VOICE / MUSIC / MODEL_3D / IMAGE_PROCESS")
                String type,
        @Schema(description = "任务状态：PREPARED / SUBMITTING / PENDING / RUNNING / SUCCESS / FAIL")
                String status,
        @Schema(description = "提供商") String provider,
        @Schema(description = "稳定供应商幂等键") String providerKey,
        @Schema(description = "模型名称") String model,
        @Schema(description = "生成 prompt") String prompt,
        @Schema(description = "第三方任务 ID") String providerTaskId,
        @Schema(description = "供应商原始响应快照") String providerResult,
        @Schema(description = "输出媒体 ID") Long outputMediaId,
        @Schema(description = "输出媒体版本 ID") Long outputMediaVersionId,
        @Schema(description = "输出媒体动态访问 URL") String outputUrl,
        @Schema(description = "已保存资产 ID，未保存时为空") Long assetId,
        @Schema(description = "是否已保存为资产") boolean isAsset,
        @Schema(description = "失败原因") String errorMsg,
        @Schema(description = "生成参数 JSON") String params,
        @Schema(description = "所属项目 ID") Long projectId,
        @Schema(description = "执行 Run ID") Long executionRunId,
        @Schema(description = "项目对象 ID") Long projectObjectId,
        @Schema(description = "幂等键") String idempotencyKey,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
