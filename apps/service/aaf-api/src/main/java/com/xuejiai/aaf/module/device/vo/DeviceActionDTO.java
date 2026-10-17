package com.xuejiai.aaf.module.device.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** EntityDef 单记录动作请求。 */
public record DeviceActionDTO(@NotNull @Positive @Schema(description = "设备 ID") Long id) {}
