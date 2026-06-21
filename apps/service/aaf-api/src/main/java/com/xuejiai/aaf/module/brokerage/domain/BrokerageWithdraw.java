package com.xuejiai.aaf.module.brokerage.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.enums.brokerage.BrokerageWithdrawStatusEnum;
import com.xuejiai.aaf.common.enums.brokerage.BrokerageWithdrawTypeEnum;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 佣金提现申请。 */
@Getter
@Setter
@Entity
@Table(name = "brokerage_withdraw")
@SQLDelete(
        sql =
                "UPDATE brokerage_withdraw SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class BrokerageWithdraw extends BaseEntity {

    /** 申请人 contact_id */
    @Column(name = "contact_id", nullable = false)
    private Long contactId;

    /** 申请提现金额（分） */
    @Column(name = "amount", nullable = false)
    private Long amount;

    /** 手续费（分） */
    @Column(name = "fee", nullable = false)
    private Long fee = 0L;

    /** 提现类型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private BrokerageWithdrawTypeEnum type;

    /** 收款人姓名 */
    @Column(name = "account_name", length = 100)
    private String accountName;

    /** 收款账号 */
    @Column(name = "account_no", length = 200)
    private String accountNo;

    /** 收款码 URL */
    @Column(name = "qr_code_url", length = 500)
    private String qrCodeUrl;

    /** 状态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BrokerageWithdrawStatusEnum status = BrokerageWithdrawStatusEnum.PENDING;

    /** 审核拒绝原因 */
    @Column(name = "audit_reason", length = 500)
    private String auditReason;

    /** 审核时间 */
    @Column(name = "audit_time")
    private LocalDateTime auditTime;

    /** 关联转账单 ID */
    @Column(name = "pay_transfer_id")
    private Long payTransferId;

    /** 转账成功时间 */
    @Column(name = "transfer_time")
    private LocalDateTime transferTime;

    /** 转账失败原因 */
    @Column(name = "transfer_error", length = 500)
    private String transferError;
}
