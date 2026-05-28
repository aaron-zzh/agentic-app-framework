package com.xuejiai.aaf.module.system.config.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 更新系统配置请求。
 *
 * @author AaronZZH & Kiro
 */
public record SystemConfigUpdateDTO(
        @Schema(description = "配置键", example = "user.default_password") @NotBlank String key,
        @Schema(description = "配置值") String value) {}
