package com.xuejiai.aaf.module.chat.livechat.seat.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.enums.chat.SeatTypeEnum;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 客服坐席实体。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "livechat_seat")
@SQLDelete(
        sql =
                "UPDATE livechat_seat SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class LivechatSeat extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type", nullable = false, length = 16)
    private SeatTypeEnum seatType = SeatTypeEnum.HUMAN;

    /** 关联用户 ID（人工坐席） */
    @Column(name = "user_id")
    private Long userId;

    /** 关联 AI 助手 ID（AI 坐席） */
    @Column(name = "assistant_id")
    private Long assistantId;

    @Column(name = "nickname", length = 64)
    private String nickname;

    @Column(name = "skill_group", length = 128)
    private String skillGroup;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "offline";

    @Column(name = "current_sessions", nullable = false)
    private Integer currentSessions = 0;

    @Column(name = "max_sessions", nullable = false)
    private Integer maxSessions = 5;

    /** 是否还有接待容量。 */
    public boolean hasCapacity() {
        return currentSessions < maxSessions;
    }

    /** 增加当前会话数。 */
    public void incrementSessions() {
        this.currentSessions++;
    }

    /** 减少当前会话数（不低于 0）。 */
    public void decrementSessions() {
        if (this.currentSessions > 0) {
            this.currentSessions--;
        }
    }
}
