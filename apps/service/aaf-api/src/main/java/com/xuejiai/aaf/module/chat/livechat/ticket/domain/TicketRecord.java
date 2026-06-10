package com.xuejiai.aaf.module.chat.livechat.ticket.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 工单操作记录实体。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ticket_record")
@SQLDelete(
        sql =
                "UPDATE ticket_record SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class TicketRecord extends BaseEntity {

    /** 关联工单 ID */
    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    /** 操作类型：ASSIGN / START / CONFIRM / CLOSE / REOPEN / COMMENT 等 */
    @Column(name = "operation", length = 16, nullable = false)
    private String operation;

    /** 操作人 ID */
    @Column(name = "operator_id")
    private Long operatorId;

    /** 操作前状态 */
    @Column(name = "from_status", length = 16)
    private String fromStatus;

    /** 操作后状态 */
    @Column(name = "to_status", length = 16)
    private String toStatus;

    /** 操作备注 */
    @Column(name = "record_remark", length = 512)
    private String recordRemark;
}
