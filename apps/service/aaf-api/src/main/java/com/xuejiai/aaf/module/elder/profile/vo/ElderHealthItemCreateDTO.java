package com.xuejiai.aaf.module.elder.profile.vo;

import java.time.LocalDate;

import com.xuejiai.aaf.common.enums.elder.HealthItemTypeEnum;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 长者健康条目创建请求。 */
@Schema(description = "创建长者健康条目")
public record ElderHealthItemCreateDTO(
        @NotNull @Positive @Schema(description = "长者 ID") Long elderId,
        @NotBlank @InEnum(value = HealthItemTypeEnum.class, message = "健康条目类型必须是 {value}")
                String itemType,
        @NotBlank @Size(max = 200) @Schema(description = "条目名称") String name,
        @Schema(description = "详细说明") String description,
        @Schema(description = "发生或确诊日期") LocalDate occurredDate) {}
