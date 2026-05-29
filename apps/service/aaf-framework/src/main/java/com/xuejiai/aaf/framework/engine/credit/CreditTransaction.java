package com.xuejiai.aaf.framework.engine.credit;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

/** 积分流水记录 */
@Getter
@Setter
@Entity
@Table(name = "credit_transaction")
@SQLDelete(
        sql =
                "UPDATE credit_transaction SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class CreditTransaction extends BaseEntity {

    /** 关联账户 ID */
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /** 流水类型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private CreditTransactionType type;

    /** 变动金额（正数） */
    @Column(name = "amount", nullable = false)
    private Long amount;

    /** 变动后余额 */
    @Column(name = "balance_after", nullable = false)
    private Long balanceAfter;

    /** 来源描述 */
    @Column(name = "source", length = 100)
    private String source;

    /** 业务单号 */
    @Column(name = "biz_id", length = 64)
    private String bizId;
}
