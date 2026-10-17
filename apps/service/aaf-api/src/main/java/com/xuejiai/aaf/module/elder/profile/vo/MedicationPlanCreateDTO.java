package com.xuejiai.aaf.module.elder.profile.vo;

import java.time.LocalDate;
import java.util.List;

import com.xuejiai.aaf.common.enums.elder.MedicationPlanSourceEnum;
import com.xuejiai.aaf.common.enums.elder.MedicationPlanStatusEnum;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 用药计划创建请求。 */
@Schema(description = "创建用药计划")
public record MedicationPlanCreateDTO(
        @NotNull @Positive @Schema(description = "长者 ID") Long elderId,
        @NotBlank @Size(max = 200) @Schema(description = "药品名称，由人提供") String medicationName,
        @NotBlank @Size(max = 500) @Schema(description = "剂量说明，由人提供") String dosageInstructions,
        @NotEmpty @Size(max = 24) @Schema(description = "每日提醒时间点，格式 HH:mm")
                List<String> reminderTimes,
        @NotNull @Schema(description = "生效日期") LocalDate startDate,
        @Schema(description = "结束日期") LocalDate endDate,
        @InEnum(value = MedicationPlanStatusEnum.class, message = "用药计划状态必须是 {value}")
                String status,
        @InEnum(value = MedicationPlanSourceEnum.class, message = "用药计划来源必须是 {value}")
                String source) {}
