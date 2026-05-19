package com.xuejiai.aaf.module.system.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 审批委托。 */
@Getter
@Setter
@Entity
@Table(name = "sys_delegation")
@SQLDelete(sql = "UPDATE sys_delegation SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class Delegation extends BaseEntity {

    /** 委托人 ID */
    @Column(name = "delegator_id", nullable = false)
    private Long delegatorId;

    /** 代理人 ID */
    @Column(name = "delegate_id", nullable = false)
    private Long delegateId;

    /** 委托开始时间 */
    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    /** 委托结束时间 */
    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    /** 适用流程 key 列表（JSONB），null 表示全部流程 */
    @Column(name = "process_keys", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String processKeys;

    /** 状态：active / expired / cancelled */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "active";
}
