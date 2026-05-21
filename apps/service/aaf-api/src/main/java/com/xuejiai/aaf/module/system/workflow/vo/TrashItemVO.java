package com.xuejiai.aaf.module.system.workflow.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** 回收站列表项。 */
@Schema(description = "回收站列表项")
public record TrashItemVO(
        @Schema(description = "记录 ID") Long id,
        @Schema(description = "实体类型") String entityType,
        @Schema(description = "标题/名称") String title,
        @Schema(description = "删除人 ID") Long deletedBy,
        @Schema(description = "删除时间") LocalDateTime deletedAt) {}
