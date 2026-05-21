package com.xuejiai.aaf.module.system.config.vo;

import jakarta.validation.constraints.NotBlank;

/** 更新系统配置请求。 */
public record SystemConfigUpdateDTO(@NotBlank String key, String value) {}
