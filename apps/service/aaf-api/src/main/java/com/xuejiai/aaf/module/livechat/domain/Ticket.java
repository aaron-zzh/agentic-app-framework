package com.xuejiai.aaf.module.livechat.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.enums.livechat.TicketPriorityEnum;
import com.xuejiai.aaf.common.enums.livechat.TicketStatusEnum;
import com.xuejiai.aaf.common.enums.livechat.TicketTypeEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 工单实体。
 *
 * <p>状态机：PENDING → PROCESSING → CONFIRMING → CLOSED
 */
@Getter
@Setter
@Entity
@Table(name = "ticket")
@SQLDelete(sql = "UPDATE ticket SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class Ticket extends BaseEntity {

    /** 工单编号（唯一） */
    @Column(name = "ticket_no", nullable = false, unique = true, length = 32)
    private String ticketNo;

    /** 工单标题 */
    @Column(name = "title", nullable = false, length = 128)
    private String title;

    /** 工单描述 */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** 提交用户 ID */
    @Column(name = "user_id")
    private Long userId;

    /** 关联会话 ID（可为空，手动创建时无会话） */
    @Column(name = "session_id")
    private Long sessionId;

    /** 工单类型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private TicketTypeEnum type;

    /** 优先级 */
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private TicketPriorityEnum priority;

    /** 工单状态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TicketStatusEnum status;

    /** 处理人 ID */
    @Column(name = "assignee_id")
    private Long assigneeId;

    /** SLA 截止时间 */
    @Column(name = "sla_due_time")
    private LocalDateTime slaDueTime;

    /** 关闭时间 */
    @Column(name = "closed_time")
    private LocalDateTime closedTime;

    /** 开始处理 */
    public void startProcessing(Long assigneeId) {
        assertStatus(TicketStatusEnum.PENDING);
        this.status = TicketStatusEnum.PROCESSING;
        this.assigneeId = assigneeId;
    }

    /** 提交确认 */
    public void submitConfirm() {
        assertStatus(TicketStatusEnum.PROCESSING);
        this.status = TicketStatusEnum.CONFIRMING;
    }

    /** 关闭工单 */
    public void close() {
        if (this.status == TicketStatusEnum.CLOSED) {
            return;
        }
        this.status = TicketStatusEnum.CLOSED;
        this.closedTime = LocalDateTime.now();
    }

    /** 重新打开 */
    public void reopen() {
        assertStatus(TicketStatusEnum.CLOSED);
        this.status = TicketStatusEnum.PENDING;
        this.closedTime = null;
    }

    private void assertStatus(TicketStatusEnum expected) {
        if (this.status != expected) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST,
                    "工单状态不正确，当前: %s，期望: %s".formatted(this.status.getLabel(), expected.getLabel()));
        }
    }
}
