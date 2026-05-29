package com.xuejiai.aaf.module.channel.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 渠道消息记录。
 *
 * <p>记录所有入站/出站消息，用于审计和排查。
 */
@Getter
@Setter
@Entity
@Table(name = "channel_message")
@SQLDelete(
        sql =
                "UPDATE channel_message SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ChannelMessage extends BaseEntity {

    /** 渠道类型 */
    @Column(name = "channel_type", nullable = false, length = 32)
    private String channelType;

    /** 消息方向：inbound / outbound */
    @Column(name = "direction", nullable = false, length = 16)
    private String direction;

    /** 消息类型：text / image / voice / event / template */
    @Column(name = "message_type", nullable = false, length = 16)
    private String messageType;

    /** 渠道侧用户标识（openid 等） */
    @Column(name = "external_user_id", nullable = false, length = 200)
    private String externalUserId;

    /** 关联系统用户 ID（已绑定时有值） */
    @Column(name = "user_id")
    private Long userId;

    /** 文本内容 */
    @Column(name = "content", columnDefinition = "text")
    private String content;

    /** 媒体 URL */
    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    /** 原始报文 */
    @Column(name = "raw_payload", columnDefinition = "text")
    private String rawPayload;

    /** 消息时间 */
    @Column(name = "message_time")
    private LocalDateTime messageTime;
}
