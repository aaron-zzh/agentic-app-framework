package com.xuejiai.aaf.module.elder.guard.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 预警单条动作请求；代记时可附联系人 ID 以形成完整审计。 */
@Schema(description = "预警单条动作请求")
public record AlertActionDTO(
        @NotNull @Positive @Schema(description = "预警 ID") Long id,
        @Positive @Schema(description = "被代记已知晓的联系人 ID") Long acknowledgedContactId) {}
