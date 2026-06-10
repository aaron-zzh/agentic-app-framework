package com.xuejiai.aaf.module.chat.livechat.ticket.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 客服工单实体。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ticket")
@SQLDelete(
        sql =
                "UPDATE ticket SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class Ticket extends BaseEntity {

    /** 工单编号，全局唯一 */
    @Column(name = "ticket_no", length = 32, nullable = false, unique = true)
    private String ticketNo;

    /** 工单标题 */
    @Column(name = "title", length = 128, nullable = false)
    private String title;

    /** 工单描述 */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** 提交用户 ID */
    @Column(name = "user_id")
    private Long userId;

    /** 关联会话 ID */
    @Column(name = "conversation_id")
    private Long conversationId;

    /** 工单类型 */
    @Column(name = "type", length = 32, nullable = false)
    private String type;

    /** 优先级：LOW / MEDIUM / HIGH / URGENT */
    @Column(name = "priority", length = 16, nullable = false)
    private String priority;

    /** 状态：PENDING / PROCESSING / CONFIRMING / CLOSED / REOPENED */
    @Column(name = "status", length = 16, nullable = false)
    private String status = "PENDING";

    /** 受理客服 ID */
    @Column(name = "assignee_id")
    private Long assigneeId;

    /** SLA 截止时间 */
    @Column(name = "sla_due_time")
    private LocalDateTime slaDueTime;

    /** 关闭时间 */
    @Column(name = "closed_time")
    private LocalDateTime closedTime;

    // ========== 业务方法 ==========

    /**
     * 开始处理工单，状态 PENDING/REOPENED → PROCESSING。
     *
     * @param assigneeId 受理客服 ID
     */
    public void startProcessing(Long assigneeId) {
        assertStatus("PENDING", "REOPENED");
        this.status = "PROCESSING";
        this.assigneeId = assigneeId;
    }

    /** 提交待确认，状态 PROCESSING → CONFIRMING。 */
    public void submitConfirm() {
        assertStatus("PROCESSING");
        this.status = "CONFIRMING";
    }

    /** 关闭工单，状态 CONFIRMING/PROCESSING → CLOSED。 */
    public void close() {
        assertStatus("CONFIRMING", "PROCESSING");
        this.status = "CLOSED";
        this.closedTime = LocalDateTime.now();
    }

    /** 重新打开工单，状态 CLOSED → REOPENED。 */
    public void reopen() {
        assertStatus("CLOSED");
        this.status = "REOPENED";
        this.closedTime = null;
    }

    /** 校验当前状态是否在允许列表中，不在则抛业务异常。 */
    private void assertStatus(String... allowed) {
        for (String s : allowed) {
            if (s.equals(this.status)) return;
        }
        throw new BusinessException(
                GlobalErrorCode.BAD_REQUEST, "工单当前状态 [" + this.status + "] 不允许执行此操作");
    }
}
