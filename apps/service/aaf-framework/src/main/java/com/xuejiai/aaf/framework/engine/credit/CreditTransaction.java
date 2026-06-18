package com.xuejiai.aaf.framework.engine.credit;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 积分流水记录 */
@Getter
@Setter
@Entity
@Table(name = "credit_transaction")
@SQLDelete(
        sql =
                "UPDATE credit_transaction SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
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

    /** 来源描述（历史字段，新代码改用枚举值） */
    @Column(name = "source", length = 100)
    private String source;

    /** 消费分类（AI 能力维度，仅 SPEND 类型有意义），对应 {@link CreditTransactionCategory} */
    @Column(name = "category", length = 32)
    private String category;

    /** 业务单号 */
    @Column(name = "biz_id", length = 64)
    private String bizId;

    /** 批次来源（EARN 时有意义）：SUBSCRIPTION/TOPUP/REWARD/WEEKLY/MANUAL */
    @Column(name = "batch_type", length = 16)
    private String batchType;

    /** 过期时间，null = 永不过期（充值积分） */
    @Column(name = "expire_at")
    private LocalDateTime expireAt;

    /** 本批次剩余可用量（EARN 时 = amount，消费后递减；SPEND/EXPIRE 时为 0） */
    @Column(name = "remain")
    private Long remain;
}
