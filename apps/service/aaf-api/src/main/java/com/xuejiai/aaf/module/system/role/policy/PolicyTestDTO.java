package com.xuejiai.aaf.module.system.role.policy;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 策略测试 Request DTO。 */
@Schema(description = "策略测试请求")
public record PolicyTestDTO(
        @Schema(description = "资源类型") @NotBlank String resourceType,
        @Schema(description = "操作") @NotBlank String action,
        @Schema(description = "上下文属性") Map<String, Object> context) {}
