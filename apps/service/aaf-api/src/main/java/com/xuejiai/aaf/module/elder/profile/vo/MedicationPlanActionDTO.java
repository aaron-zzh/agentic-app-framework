package com.xuejiai.aaf.module.elder.profile.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 用药计划单条动作请求。 */
@Schema(description = "用药计划单条动作请求")
public record MedicationPlanActionDTO(
        @NotNull @Positive @Schema(description = "用药计划 ID") Long id) {}
