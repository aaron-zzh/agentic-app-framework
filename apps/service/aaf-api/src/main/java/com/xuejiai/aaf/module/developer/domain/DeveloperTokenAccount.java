package com.xuejiai.aaf.module.developer.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 开发者托管模型总 Token 池。 */
@Getter
@Setter
@Entity
@Table(name = "developer_token_account")
@SQLDelete(
        sql =
                "UPDATE developer_token_account SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class DeveloperTokenAccount extends BaseEntity {

    @Column(name = "developer_id", nullable = false, unique = true)
    private Long developerId;

    @Column(name = "balance_tokens", nullable = false)
    private Long balanceTokens = 0L;

    @Column(name = "frozen_tokens", nullable = false)
    private Long frozenTokens = 0L;

    @Column(name = "total_earned_tokens", nullable = false)
    private Long totalEarnedTokens = 0L;

    @Column(name = "total_spent_tokens", nullable = false)
    private Long totalSpentTokens = 0L;
}
