package com.xuejiai.aaf.module.brokerage.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.enums.brokerage.BrokerageRecordStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 佣金流水。含规则快照字段，防止规则变更后历史账对不上。 */
@Getter
@Setter
@Entity
@Table(name = "brokerage_record")
@SQLDelete(
        sql =
                "UPDATE brokerage_record SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class BrokerageRecord extends BaseEntity {

    /** 获佣分销员 contact_id */
    @Column(name = "contact_id", nullable = false)
    private Long contactId;

    /** 触发来源联系人（消费者）contact_id */
    @Column(name = "source_contact_id")
    private Long sourceContactId;

    /** 推广层级：1=直接推广 2=间接推广 */
    @Column(name = "source_level", nullable = false)
    private Short sourceLevel = 1;

    /** 业务类型：ORDER/SUBSCRIBE/RECHARGE/INVITE/ORDER_REFUND */
    @Column(name = "biz_type", nullable = false, length = 32)
    private String bizType;

    /** 关联业务 ID */
    @Column(name = "biz_id", length = 64)
    private String bizId;

    /** 标题 */
    @Column(name = "title", length = 200)
    private String title;

    /** 佣金金额（分），负数=退款冲回 */
    @Column(name = "amount", nullable = false)
    private Long amount;

    /** 状态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BrokerageRecordStatusEnum status = BrokerageRecordStatusEnum.FROZEN;

    /** 冻结天数 */
    @Column(name = "frozen_days", nullable = false)
    private Integer frozenDays = 0;

    /** 解冻时间 */
    @Column(name = "unfreeze_time")
    private LocalDateTime unfreezeTime;

    // ========== 规则快照 ==========

    /** 命中的规则 ID（快照） */
    @Column(name = "rule_id")
    private Long ruleId;

    /** 实际使用的佣金比例（快照） */
    @Column(name = "applied_rate", precision = 5, scale = 4)
    private BigDecimal appliedRate;

    /** 计算基数（快照，如实付金额，分） */
    @Column(name = "calc_base_amount")
    private Long calcBaseAmount;
}
