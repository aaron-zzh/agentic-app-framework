package com.xuejiai.aaf.module.channel.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Webhook 配置保存入参。
 *
 * <p>secret 留空表示不修改（出参已脱敏，客户端无法回填原值）。
 */
public record WebhookConfigSaveDTO(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 500) String url,
        @Size(max = 500) String eventTypes,
        @Size(max = 200) String secret,
        @Size(max = 16) String status,
        @Size(max = 16) String direction,
        @Min(0) @Max(10) Integer maxRetries) {}
