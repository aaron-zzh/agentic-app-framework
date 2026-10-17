package com.xuejiai.aaf.module.elder.guard.vo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.xuejiai.aaf.common.constant.DictType;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.framework.crud.export.DictFormat;

import io.swagger.v3.oas.annotations.media.Schema;

/** 预警处理审计响应。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "预警处理记录")
public record AlertHandlingVO(
        Long id,
        Long alertId,
        @DictFormat(DictType.Elder.ALERT_HANDLING_TYPE) String handlingType,
        @DictFormat(DictType.Elder.ALERT_HANDLING_CHANNEL) String channel,
        @DictFormat(DictType.Elder.ALERT_HANDLING_STATUS) String status,
        @DictFormat(DictType.Elder.ALERT_RECIPIENT_TYPE) String recipientType,
        Long recipientId,
        Long operatorUserId,
        Long acknowledgedContactId,
        String detail,
        String errorMessage,
        LocalDateTime handledAt,
        LocalDateTime createTime,
        ResourceRefDTO alert,
        ResourceRefDTO operatorUser,
        ResourceRefDTO acknowledgedContact,
        ResourceRefDTO createBy) {}
