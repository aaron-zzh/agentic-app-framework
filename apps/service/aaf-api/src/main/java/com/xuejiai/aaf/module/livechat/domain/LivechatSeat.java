package com.xuejiai.aaf.module.livechat.domain;

import com.xuejiai.aaf.common.enums.livechat.SeatStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 客服坐席实体。 */
@Getter
@Setter
@Entity
@Table(name = "livechat_seat")
public class LivechatSeat extends BaseEntity {

    /** 关联系统用户 ID */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /** 坐席昵称 */
    @Column(name = "nickname", length = 64)
    private String nickname;

    /** 技能组（逗号分隔，支持多技能） */
    @Column(name = "skill_group", length = 128)
    private String skillGroup;

    /** 坐席状态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SeatStatusEnum status;

    /** 当前会话数 */
    @Column(name = "current_sessions", nullable = false)
    private Integer currentSessions = 0;

    /** 最大并发会话数 */
    @Column(name = "max_sessions", nullable = false)
    private Integer maxSessions = 5;

    /** 是否有空闲容量 */
    public boolean hasCapacity() {
        return status == SeatStatusEnum.ONLINE && currentSessions < maxSessions;
    }

    /** 增加会话计数 */
    public void incrementSessions() {
        this.currentSessions++;
        if (currentSessions >= maxSessions) {
            this.status = SeatStatusEnum.BUSY;
        }
    }

    /** 减少会话计数 */
    public void decrementSessions() {
        if (currentSessions > 0) {
            this.currentSessions--;
        }
        if (status == SeatStatusEnum.BUSY && currentSessions < maxSessions) {
            this.status = SeatStatusEnum.ONLINE;
        }
    }
}
