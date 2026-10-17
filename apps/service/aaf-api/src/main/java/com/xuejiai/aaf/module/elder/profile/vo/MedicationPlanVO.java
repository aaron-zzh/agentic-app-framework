package com.xuejiai.aaf.module.elder.profile.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.xuejiai.aaf.common.constant.DictType;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.framework.crud.export.DictFormat;

import io.swagger.v3.oas.annotations.media.Schema;

/** 用药计划响应。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "用药计划")
public record MedicationPlanVO(
        Long id,
        Integer version,
        Long elderId,
        String medicationName,
        String dosageInstructions,
        List<String> reminderTimes,
        LocalDate startDate,
        LocalDate endDate,
        @DictFormat(DictType.Elder.MEDICATION_PLAN_STATUS) String status,
        @DictFormat(DictType.Elder.MEDICATION_PLAN_SOURCE) String source,
        LocalDateTime nextReminderAt,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        ResourceRefDTO elder,
        ResourceRefDTO createBy,
        ResourceRefDTO updateBy) {}
