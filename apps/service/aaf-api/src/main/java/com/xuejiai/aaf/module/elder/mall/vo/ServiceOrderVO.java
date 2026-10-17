package com.xuejiai.aaf.module.elder.mall.vo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.xuejiai.aaf.common.constant.DictType;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.framework.crud.export.DictFormat;

import io.swagger.v3.oas.annotations.media.Schema;

/** 服务预约响应。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "服务预约")
public record ServiceOrderVO(
        Long id,
        Integer version,
        Long elderId,
        Long serviceItemId,
        Integer quantity,
        LocalDateTime scheduledAt,
        String serviceAddress,
        String notes,
        @DictFormat(DictType.Elder.SERVICE_ORDER_STATUS) String status,
        String reminderCommandId,
        LocalDateTime confirmedAt,
        LocalDateTime completedAt,
        LocalDateTime cancelledAt,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        ResourceRefDTO elder,
        ResourceRefDTO serviceItem,
        ResourceRefDTO createBy,
        ResourceRefDTO updateBy) {}
