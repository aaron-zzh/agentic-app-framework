package com.xuejiai.aaf.module.system.task.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 待办更新请求。
 *
 * @author AaronZZH & Kiro
 */
public record TodoUpdateDTO(
        @Schema(description = "待办标题") String title,
        @Schema(description = "分类：todo / call / email / meeting") String category,
        @Schema(description = "状态：pending / done / ignored") String status,
        @Schema(description = "截止时间") LocalDateTime dueDate) {}
