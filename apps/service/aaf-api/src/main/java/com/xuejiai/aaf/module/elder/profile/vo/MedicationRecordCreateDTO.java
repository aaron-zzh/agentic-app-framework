package com.xuejiai.aaf.module.elder.profile.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.enums.elder.MedicationRecordResultEnum;
import com.xuejiai.aaf.common.enums.elder.MedicationRecordSourceEnum;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 服药事实创建请求。 */
@Schema(description = "创建服药记录")
public record MedicationRecordCreateDTO(
        @NotNull @Positive @Schema(description = "用药计划 ID") Long planId,
        @NotNull @Schema(description = "计划服药时间") LocalDateTime scheduledAt,
        @NotBlank @InEnum(value = MedicationRecordResultEnum.class, message = "服药结果必须是 {value}")
                String result,
        @NotBlank @InEnum(value = MedicationRecordSourceEnum.class, message = "记录来源必须是 {value}")
                String source,
        @Schema(description = "实际确认或判定时间，未传时取当前时间") LocalDateTime recordedAt,
        @Size(max = 100) @Schema(description = "来源记录引用") String sourceReference) {}
