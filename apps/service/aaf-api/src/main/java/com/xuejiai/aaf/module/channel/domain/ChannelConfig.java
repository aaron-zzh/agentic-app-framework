package com.xuejiai.aaf.module.channel.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 渠道配置。
 *
 * <p>存储各渠道的 appId/secret/token 等配置信息。
 */
@Getter
@Setter
@Entity
@Table(name = "channel_config")
@SQLDelete(
        sql =
                "UPDATE channel_config SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ChannelConfig extends BaseEntity {

    /** 渠道类型（wechat_mp/wechat_mini/dingtalk/feishu） */
    @Column(name = "channel_type", nullable = false, length = 32)
    private String channelType;

    /** 渠道名称 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 应用 ID（appId / corpId） */
    @Column(name = "app_id", length = 200)
    private String appId;

    /** 应用密钥（加密存储） */
    @Column(name = "app_secret", length = 500)
    private String appSecret;

    /** 消息 Token（微信验证用） */
    @Column(name = "token", length = 200)
    private String token;

    /** 消息加密 Key */
    @Column(name = "encoding_aes_key", length = 200)
    private String encodingAesKey;

    /** 状态：0 启用 / 1 禁用 */
    @Column(name = "status", nullable = false)
    private Integer status = 0;

    /** 扩展配置（JSON） */
    @Column(name = "ext_config", columnDefinition = "jsonb")
    private String extConfig;
}
