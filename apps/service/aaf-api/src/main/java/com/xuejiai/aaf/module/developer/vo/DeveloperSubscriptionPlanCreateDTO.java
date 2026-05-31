package com.xuejiai.aaf.module.developer.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 创建开发者订阅套餐请求。 */
public record DeveloperSubscriptionPlanCreateDTO(
        @NotBlank(message = "套餐编码不能为空") @Size(max = 40) @Schema(description = "套餐编码")
                String code,
        @NotBlank(message = "套餐名称不能为空") @Size(max = 100) @Schema(description = "套餐名称")
                String name,
        @NotNull(message = "有效天数不能为空") @Min(0) @Schema(description = "有效天数，0 表示永久")
                Integer durationDays,
        @NotNull(message = "价格不能为空") @Min(0) @Schema(description = "价格，单位：分")
                Long price,
        @NotNull(message = "包含 Token 数不能为空") @Min(0) @Schema(description = "套餐包含 Token 数")
                Long includedTokens,
        @Schema(description = "是否允许托管模型网关") Boolean allowManagedGateway,
        @Schema(description = "是否允许开通子代理") Boolean allowSubProxy,
        @Min(0) @Schema(description = "最大子代理层级") Integer maxProxyDepth,
        @Size(max = 20) @Schema(description = "状态：ENABLED/DISABLED") String status,
        @Schema(description = "排序") Integer sortOrder) {}
