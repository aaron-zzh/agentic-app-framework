package com.xuejiai.aaf.module.elder.mall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 服务项目单条管理动作请求。 */
@Schema(description = "服务项目单条管理动作请求")
public record ServiceItemActionDTO(@NotNull @Positive @Schema(description = "服务项目 ID") Long id) {}
