package com.xuejiai.aaf.module.elder.guard.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.xuejiai.aaf.common.constant.DictType;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.framework.crud.export.DictFormat;

import io.swagger.v3.oas.annotations.media.Schema;

/** 预警事件响应。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "预警事件")
public record AlertEventVO(
        Long id,
        Integer version,
        Long deviceId,
        Long elderId,
        String eventId,
        @DictFormat(DictType.Elder.ALERT_TYPE) String alertType,
        @DictFormat(DictType.Elder.ALERT_LEVEL) String level,
        @DictFormat(DictType.Elder.ALERT_STATUS) String status,
        @DictFormat(DictType.Device.DEVICE_ZONE) String zone,
        BigDecimal confidence,
        String payload,
        LocalDateTime deviceOccurredAt,
        LocalDateTime receivedAt,
        Boolean late,
        LocalDateTime nextActionAt,
        Long claimedBy,
        LocalDateTime claimedAt,
        LocalDateTime acknowledgedAt,
        LocalDateTime resolvedAt,
        String remark,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        ResourceRefDTO device,
        ResourceRefDTO elder,
        ResourceRefDTO claimedByUser,
        ResourceRefDTO createBy,
        ResourceRefDTO updateBy) {}
