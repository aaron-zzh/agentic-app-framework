package com.xuejiai.aaf.module.elder.profile.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.xuejiai.aaf.common.constant.DictType;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.framework.crud.export.DictFormat;

import io.swagger.v3.oas.annotations.media.Schema;

/** 长者档案响应；身份、联系方式、地址、偏好及联系人仅在 detail 字段集输出。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "长者档案")
public record ElderVO(
        Long id,
        Integer version,
        Long userId,
        String fullName,
        @DictFormat(DictType.Sys.USER_SEX) String gender,
        LocalDate birthDate,
        String idCardNo,
        String mobile,
        String address,
        @DictFormat(DictType.Elder.LIVING_ARRANGEMENT) String livingArrangement,
        String preferredName,
        String preferences,
        String taboos,
        @DictFormat(DictType.Elder.ELDER_STATUS) String status,
        Boolean cameraConsent,
        Long cameraConsentBy,
        LocalDateTime cameraConsentAt,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        ResourceRefDTO user,
        ResourceRefDTO cameraConsentUser,
        List<ElderContactSummaryVO> contacts,
        List<ElderHealthItemVO> healthItems,
        List<MedicationPlanVO> medicationPlans,
        List<MedicationRecordVO> medicationRecords,
        ResourceRefDTO createBy,
        ResourceRefDTO updateBy) {}
