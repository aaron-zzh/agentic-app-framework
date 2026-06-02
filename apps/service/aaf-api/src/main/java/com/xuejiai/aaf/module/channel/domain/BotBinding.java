package com.xuejiai.aaf.module.channel.domain;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 机器人绑定——将平台上的机器人实例绑定到 Assistant。
 *
 * <p>一个 {@link ChannelPlatform} 下可创建多个 BotBinding， 实现"一个钉钉应用 → 多个机器人 → 各绑定不同 Assistant"。
 */
@Getter
@Setter
@Entity
@Table(name = "channel_bot_binding")
public class BotBinding extends BaseEntity {

    /** 关联的平台 ID */
    @Column(name = "platform_id", nullable = false)
    private Long platformId;

    /** 机器人名称 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 绑定的 Assistant ID */
    @Column(name = "assistant_id", nullable = false, length = 64)
    private String assistantId;

    /** 触发规则 JSON（可选，如关键词匹配、群 ID 过滤等） */
    @Column(name = "route_rule", columnDefinition = "jsonb")
    private String routeRule;

    /** 兜底回复（Assistant 不可用时） */
    @Column(name = "fallback_reply", length = 500)
    private String fallbackReply;

    /** 状态：0 启用 / 1 禁用 */
    @Column(name = "status", nullable = false)
    private Integer status = 0;
}
