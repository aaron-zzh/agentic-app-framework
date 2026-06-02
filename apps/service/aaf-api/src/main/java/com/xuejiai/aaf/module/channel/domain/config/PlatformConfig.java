package com.xuejiai.aaf.module.channel.domain.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.common.enums.channel.ChannelTypeEnum;

/**
 * 平台配置——按渠道类型解析为对应的强类型 record。
 *
 * <p>数据库存储为 jsonb，读取时通过 {@link #parse} 按 type 反序列化为具体实现。
 */
public sealed interface PlatformConfig
        permits DingtalkConfig, FeishuConfig, WecomConfig, WechatMpConfig, WechatMiniConfig {

    /** 按渠道类型解析 JSON 为对应配置 */
    static PlatformConfig parse(ChannelTypeEnum type, String json, ObjectMapper mapper) {
        try {
            return switch (type) {
                case DINGTALK -> mapper.readValue(json, DingtalkConfig.class);
                case FEISHU -> mapper.readValue(json, FeishuConfig.class);
                case WECOM_KF -> mapper.readValue(json, WecomConfig.class);
                case WECHAT_MP -> mapper.readValue(json, WechatMpConfig.class);
                case WECHAT_MINI -> mapper.readValue(json, WechatMiniConfig.class);
                case WEBHOOK, WEB -> mapper.readValue(json, DingtalkConfig.class); // 兜底
            };
        } catch (Exception e) {
            throw new IllegalArgumentException("解析平台配置失败: type=" + type, e);
        }
    }
}
