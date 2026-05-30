package com.xuejiai.aaf.module.livechat.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.enums.channel.ChannelTypeEnum;
import com.xuejiai.aaf.common.enums.livechat.SessionStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 客服会话实体。
 *
 * <p>记录一次完整的客服对话，从用户发起到关闭。
 * 状态流转：BOT → WAITING → ACTIVE → CLOSED
 */
@Getter
@Setter
@Entity(name = "LivechatChatSession")
@Table(name = "chat_session")
@SQLDelete(sql = "UPDATE chat_session SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ChatSession extends BaseEntity {

    /** 系统用户 ID（已注册用户，可为空） */
    @Column(name = "user_id")
    private Long userId;

    /** 渠道侧用户标识（openid 等） */
    @Column(name = "external_user_id", nullable = false, length = 128)
    private String externalUserId;

    /** 渠道类型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 32)
    private ChannelTypeEnum channelType;

    /** 会话状态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SessionStatusEnum status;

    /** 关联的 AI Agent ID（BOT 模式下使用） */
    @Column(name = "agent_id")
    private Long agentId;

    /** 当前服务坐席 ID */
    @Column(name = "staff_id")
    private Long staffId;

    /** 技能组 */
    @Column(name = "skill_group", length = 64)
    private String skillGroup;

    /** 会话标签（逗号分隔） */
    @Column(name = "tags", length = 256)
    private String tags;

    /** 优先级（1-5，5最高） */
    @Column(name = "priority")
    private Integer priority;

    /** 最后活跃时间 */
    @Column(name = "last_active_time")
    private LocalDateTime lastActiveTime;

    /** 会话关闭时间 */
    @Column(name = "closed_time")
    private LocalDateTime closedTime;

    /** 转人工 */
    public void transferToHuman() {
        this.status = SessionStatusEnum.WAITING;
        this.lastActiveTime = LocalDateTime.now();
    }

    /** 坐席接入 */
    public void assignStaff(Long staffId) {
        this.staffId = staffId;
        this.status = SessionStatusEnum.ACTIVE;
        this.lastActiveTime = LocalDateTime.now();
    }

    /** 关闭会话 */
    public void close() {
        this.status = SessionStatusEnum.CLOSED;
        this.closedTime = LocalDateTime.now();
    }
}
