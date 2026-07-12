package com.xuejiai.aaf.module.system.task.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.enums.sys.TodoCategoryEnum;
import com.xuejiai.aaf.common.enums.sys.TodoStatusEnum;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 待办更新请求。
 *
 * @author AaronZZH & Kiro
 */
public record TodoUpdateDTO(
        @Schema(description = "待办标题") String title,
        @InEnum(value = TodoCategoryEnum.class, message = "分类必须是 {value}")
                @Schema(description = "分类：todo / call / email / meeting")
                String category,
        @InEnum(value = TodoStatusEnum.class, message = "状态必须是 {value}")
                @Schema(description = "状态：pending / done / ignored")
                String status,
        @Schema(description = "截止时间") LocalDateTime dueDate) {}
