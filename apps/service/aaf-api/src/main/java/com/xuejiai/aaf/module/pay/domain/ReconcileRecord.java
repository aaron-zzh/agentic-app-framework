package com.xuejiai.aaf.module.pay.domain;

import java.time.LocalDate;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.enums.pay.ReconcileStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 对账记录 */
@Getter
@Setter
@Entity
@Table(name = "pay_reconcile_record")
@SQLDelete(
        sql =
                "UPDATE pay_reconcile_record SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ReconcileRecord extends BaseEntity {

    /** 对账日期 */
    @Column(name = "reconcile_date", nullable = false)
    private LocalDate reconcileDate;

    /** 渠道编码 */
    @Column(name = "channel_code", nullable = false, length = 32)
    private String channelCode;

    /** 总笔数 */
    @Column(name = "total_count", nullable = false)
    private Integer totalCount = 0;

    /** 对平笔数 */
    @Column(name = "matched_count", nullable = false)
    private Integer matchedCount = 0;

    /** 差异笔数 */
    @Column(name = "mismatch_count", nullable = false)
    private Integer mismatchCount = 0;

    /** 对账状态 */
    @Column(name = "status", nullable = false)
    private Integer status = ReconcileStatusEnum.PENDING.getCode();

    /** 差异明细（JSON） */
    @Column(name = "diff_details", columnDefinition = "TEXT")
    private String diffDetails;

    /** 总收入（分） */
    @Column(name = "total_income", nullable = false)
    private Long totalIncome = 0L;

    /** 总退款（分） */
    @Column(name = "total_refund", nullable = false)
    private Long totalRefund = 0L;

    /** 总手续费（分） */
    @Column(name = "total_fee", nullable = false)
    private Long totalFee = 0L;
}
