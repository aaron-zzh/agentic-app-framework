package com.xuejiai.aaf.module.system.task.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 待办响应。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "待办信息")
public record TodoVO(
        Long id,
        Long assigneeId,
        String title,
        String category,
        String sourceType,
        String sourceEntity,
        Long sourceId,
        String status,
        LocalDateTime dueDate,
        LocalDateTime createTime) {}
