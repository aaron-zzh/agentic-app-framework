package com.xuejiai.aaf.module.livechat.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.enums.livechat.TicketOperationEnum;
import com.xuejiai.aaf.common.enums.livechat.TicketStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 工单流转记录实体。 */
@Getter
@Setter
@Entity
@Table(name = "ticket_record")
@SQLDelete(
        sql =
                "UPDATE ticket_record SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class TicketRecord extends BaseEntity {

    /** 工单 ID */
    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    /** 操作类型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 16)
    private TicketOperationEnum operation;

    /** 操作人 ID */
    @Column(name = "operator_id")
    private Long operatorId;

    /** 原状态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 16)
    private TicketStatusEnum fromStatus;

    /** 目标状态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 16)
    private TicketStatusEnum toStatus;

    /** 备注 */
    @Column(name = "record_remark", length = 512)
    private String recordRemark;
}
