package com.xuejiai.aaf.module.stats.domain;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;

/**
 * 积分消耗排行只读实体（映射视图 v_credit_spend_ranking）。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Entity
@Immutable
@Subselect(
        "SELECT user_id, user_name, total_spent_credits, total_earned_credits, current_balance, spend_rank FROM v_credit_spend_ranking")
public class CreditSpendRanking {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "total_spent_credits")
    private Long totalSpentCredits;

    @Column(name = "total_earned_credits")
    private Long totalEarnedCredits;

    @Column(name = "current_balance")
    private Long currentBalance;

    @Column(name = "spend_rank")
    private Integer spendRank;
}
