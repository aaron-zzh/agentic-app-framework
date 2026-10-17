package com.xuejiai.aaf.module.elder.profile.vo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.xuejiai.aaf.common.constant.DictType;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.framework.crud.export.DictFormat;

import io.swagger.v3.oas.annotations.media.Schema;

/** 服药事实响应。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "服药记录")
public record MedicationRecordVO(
        Long id,
        Long elderId,
        Long planId,
        LocalDateTime scheduledAt,
        @DictFormat(DictType.Elder.MEDICATION_RECORD_RESULT) String result,
        @DictFormat(DictType.Elder.MEDICATION_RECORD_SOURCE) String source,
        LocalDateTime recordedAt,
        String sourceReference,
        LocalDateTime createTime,
        ResourceRefDTO plan,
        ResourceRefDTO elder,
        ResourceRefDTO createBy) {}
