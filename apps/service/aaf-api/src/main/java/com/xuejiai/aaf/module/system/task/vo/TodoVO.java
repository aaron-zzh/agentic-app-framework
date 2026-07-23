package com.xuejiai.aaf.module.system.task.vo;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.xuejiai.aaf.common.constant.DictType;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.framework.crud.export.DictFormat;

import io.swagger.v3.oas.annotations.media.Schema;

/** 待办响应。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "待办信息")
public record TodoVO(
        Long id,
        Integer version,
        Long assigneeId,
        String title,
        @DictFormat(DictType.Sys.TODO_CATEGORY) String category,
        String sourceType,
        ResourceRefDTO source,
        @DictFormat(DictType.Sys.TODO_STATUS) String status,
        LocalDateTime dueDate,
        LocalDateTime createTime,
        ResourceRefDTO assignee,
        List<ResourceRefDTO> participants,
        ResourceRefDTO createBy,
        ResourceRefDTO updateBy) {}
