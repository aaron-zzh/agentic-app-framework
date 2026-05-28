package com.xuejiai.aaf.module.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 批量导入进度 VO。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "批量导入进度")
public record BatchImportProgressVO(
        @Schema(description = "总文档数") int total,
        @Schema(description = "已完成数") int completed,
        @Schema(description = "失败数") int failed,
        @Schema(description = "状态（PROCESSING/COMPLETED/FAILED）") String status) {}
