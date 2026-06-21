package com.xuejiai.aaf.module.brokerage.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 分销员。关联 sys_contact，一个联系人最多一条记录。 */
@Getter
@Setter
@Entity
@Table(name = "brokerage_user")
@SQLDelete(
        sql =
                "UPDATE brokerage_user SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class BrokerageUser extends BaseEntity {

    /** 关联 sys_contact.id */
    @Column(name = "contact_id", nullable = false)
    private Long contactId;

    /** 推荐人 contact_id（上级分销员） */
    @Column(name = "referrer_contact_id")
    private Long referrerContactId;

    /** 推荐人绑定时间 */
    @Column(name = "referrer_bind_time")
    private LocalDateTime referrerBindTime;

    /** 是否有分销资格 */
    @Column(name = "brokerage_enabled", nullable = false)
    private Boolean brokerageEnabled = false;

    /** 成为分销员时间 */
    @Column(name = "brokerage_time")
    private LocalDateTime brokerageTime;

    /** 可用佣金（分） */
    @Column(name = "balance", nullable = false)
    private Long balance = 0L;

    /** 冻结佣金（分） */
    @Column(name = "frozen", nullable = false)
    private Long frozen = 0L;
}
