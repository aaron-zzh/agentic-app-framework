package com.xuejiai.aaf.module.livechat.domain;

import com.xuejiai.aaf.common.enums.livechat.TransferReasonEnum;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 会话转接记录。
 */
@Getter
@Setter
@Entity
@Table(name = "session_transfer")
public class SessionTransfer extends BaseEntity {

    /** 会话 ID */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 转出坐席 ID */
    @Column(name = "from_staff_id", nullable = false)
    private Long fromStaffId;

    /** 转入坐席 ID（为空表示转入技能组待分配） */
    @Column(name = "to_staff_id")
    private Long toStaffId;

    /** 转入技能组 */
    @Column(name = "to_skill_group", length = 64)
    private String toSkillGroup;

    /** 转接原因 */
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 32)
    private TransferReasonEnum reason;

    /** 转接备注 */
    @Column(name = "note", length = 512)
    private String note;
}
