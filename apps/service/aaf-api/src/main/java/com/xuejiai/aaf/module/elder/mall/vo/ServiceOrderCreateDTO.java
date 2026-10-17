package com.xuejiai.aaf.module.elder.mall.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 服务预约创建请求；创建结果固定为待确认。 */
@Schema(description = "创建待确认服务预约")
public record ServiceOrderCreateDTO(
        @NotNull @Positive @Schema(description = "长者 ID") Long elderId,
        @NotNull @Positive @Schema(description = "服务项目 ID") Long serviceItemId,
        @NotNull @Positive @Schema(description = "数量") Integer quantity,
        @NotNull @Future @Schema(description = "预约服务时间") LocalDateTime scheduledAt,
        @NotBlank @Size(max = 500) @Schema(description = "服务地址") String serviceAddress,
        @Size(max = 1000) @Schema(description = "预约备注") String notes) {}
