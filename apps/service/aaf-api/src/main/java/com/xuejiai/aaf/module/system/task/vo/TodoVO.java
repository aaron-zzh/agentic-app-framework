package com.xuejiai.aaf.module.system.task.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.constant.DictType;
import com.xuejiai.aaf.framework.crud.DictFormat;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 待办响应。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "待办信息")
public record TodoVO(
        @Schema(description = "待办 ID") Long id,
        @Schema(description = "执行人 ID") Long assigneeId,
        @Schema(description = "待办标题") String title,
        @DictFormat(DictType.Sys.TODO_CATEGORY)
                @Schema(description = "分类：todo / call / email / meeting")
                String category,
        @Schema(description = "来源类型，如 comment / task，标识待办由何种业务动作产生") String sourceType,
        @Schema(description = "来源实体类型，如 order / contract，标识关联的业务实体") String sourceEntity,
        @Schema(description = "来源实体 ID，配合 sourceEntity 定位具体记录") Long sourceId,
        @DictFormat(DictType.Sys.TODO_STATUS) @Schema(description = "状态：pending / done / ignored")
                String status,
        @Schema(description = "截止时间") LocalDateTime dueDate,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
