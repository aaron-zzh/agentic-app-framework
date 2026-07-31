package com.xuejiai.aaf.module.channel.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.module.channel.domain.ChannelConfig;

/**
 * 渠道配置视图对象。
 *
 * <p>密钥类字段（appSecret/token/encodingAesKey）不出参，仅以布尔标记表示"是否已配置"，避免管理接口回传明文密钥。
 */
public record ChannelConfigVO(
        Long id,
        String channelType,
        String name,
        String appId,
        boolean appSecretConfigured,
        boolean tokenConfigured,
        boolean encodingAesKeyConfigured,
        Integer status,
        String extConfig,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public static ChannelConfigVO from(ChannelConfig entity) {
        return new ChannelConfigVO(
                entity.getId(),
                entity.getChannelType(),
                entity.getName(),
                entity.getAppId(),
                isConfigured(entity.getAppSecret()),
                isConfigured(entity.getToken()),
                isConfigured(entity.getEncodingAesKey()),
                entity.getStatus(),
                entity.getExtConfig(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }

    private static boolean isConfigured(String value) {
        return value != null && !value.isBlank();
    }
}
