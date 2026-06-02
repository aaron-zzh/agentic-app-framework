package com.xuejiai.aaf.module.channel.domain;

import com.xuejiai.aaf.common.enums.channel.ChannelTypeEnum;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 渠道平台配置——存储各第三方平台的基础凭证。
 *
 * <p>一个平台（如"公司钉钉"）对应一条记录，config 字段按 type 存储不同结构的 JSON。 同一平台下可绑定多个机器人实例（见 {@link BotBinding}）。
 */
@Getter
@Setter
@Entity
@Table(name = "channel_platform")
public class ChannelPlatform extends BaseEntity {

    /** 平台类型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private ChannelTypeEnum type;

    /** 平台名称（用户自定义，如"公司钉钉"） */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 平台配置 JSON（各平台字段不同，按 type 解析） */
    @Column(name = "config", columnDefinition = "jsonb")
    private String config;

    /** 状态：0 启用 / 1 禁用 */
    @Column(name = "status", nullable = false)
    private Integer status = 0;
}
