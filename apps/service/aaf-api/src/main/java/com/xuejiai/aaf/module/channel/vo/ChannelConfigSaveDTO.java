package com.xuejiai.aaf.module.channel.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 渠道配置保存入参。
 *
 * <p>密钥类字段留空表示不修改（出参已脱敏，客户端无法回填原值）。
 */
public record ChannelConfigSaveDTO(
        @NotBlank @Size(max = 32) String channelType,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 200) String appId,
        @Size(max = 500) String appSecret,
        @Size(max = 200) String token,
        @Size(max = 200) String encodingAesKey,
        Integer status,
        String extConfig) {}
