package com.xuejiai.aaf.module.chat.conversation.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.module.chat.enums.ParticipantLeftReason;
import com.xuejiai.aaf.module.chat.enums.ParticipantRole;
import com.xuejiai.aaf.module.chat.enums.ParticipantType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 会话参与方：支持 HUMAN/ASSISTANT/AGENT/STAFF/BOT 动态进出。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "conversation_participant")
public class ConversationParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    /** 统一字符串 ID：user_id 转字符串 / assistant_id / agent_id */
    @Column(name = "participant_id", nullable = false, length = 64)
    private String participantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_type", nullable = false, length = 16)
    private ParticipantType participantType;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private ParticipantRole role = ParticipantRole.MEMBER;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt = LocalDateTime.now();

    /** NULL 表示仍在会话中 */
    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "left_reason", length = 32)
    private ParticipantLeftReason leftReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime = LocalDateTime.now();
}
