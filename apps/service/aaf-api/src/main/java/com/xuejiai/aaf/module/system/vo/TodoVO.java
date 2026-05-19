package com.xuejiai.aaf.module.system.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** 待办响应。 */
@Schema(description = "待办信息")
public record TodoVO(
        Long id,
        Long assigneeId,
        String title,
        String sourceType,
        String sourceEntity,
        Long sourceId,
        String status,
        LocalDate dueDate,
        LocalDateTime createTime) {}
