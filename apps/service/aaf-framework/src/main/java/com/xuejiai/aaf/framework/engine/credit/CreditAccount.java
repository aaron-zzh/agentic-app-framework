package com.xuejiai.aaf.framework.engine.credit;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 积分账户 */
@Getter
@Setter
@Entity
@Table(
        name = "credit_account",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_credit_account_user", columnNames = "user_id"))
@SQLDelete(
        sql =
                "UPDATE credit_account SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class CreditAccount extends BaseEntity {

    /** 用户 ID（唯一） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 可用余额 */
    @Column(name = "balance", nullable = false)
    private Long balance = 0L;

    /** 冻结金额 */
    @Column(name = "frozen", nullable = false)
    private Long frozen = 0L;

    /** 累计赚取 */
    @Column(name = "total_earned", nullable = false)
    private Long totalEarned = 0L;

    /** 累计消费 */
    @Column(name = "total_spent", nullable = false)
    private Long totalSpent = 0L;

    /** 成长经验值（wallet 复用，AAF-074） */
    @Column(name = "exp", nullable = false)
    private Integer exp = 0;

    /** 当前等级 ID（wallet 复用，AAF-074） */
    @Column(name = "level_id")
    private Long levelId;
}
