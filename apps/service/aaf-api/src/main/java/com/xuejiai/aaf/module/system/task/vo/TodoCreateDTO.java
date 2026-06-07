package com.xuejiai.aaf.module.system.task.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 待办创建请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建待办")
public record TodoCreateDTO(
        @NotBlank @Schema(description = "待办标题") String title,
        @Schema(description = "分类：todo / call / email / meeting，默认 todo") String category,
        @Schema(description = "指派人 ID，不传则指派给当前用户") Long assigneeId,
        @Schema(description = "来源实体类型") String sourceEntity,
        @Schema(description = "来源实体 ID") Long sourceId,
        @Schema(description = "截止时间") LocalDateTime dueDate) {}
