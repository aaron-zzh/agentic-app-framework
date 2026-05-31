package com.xuejiai.aaf.module.developer.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 开发者 Token 池流水。 */
@Getter
@Setter
@Entity
@Table(name = "developer_token_transaction")
@SQLDelete(
        sql =
                "UPDATE developer_token_transaction SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class DeveloperTokenTransaction extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "developer_id", nullable = false)
    private Long developerId;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "amount_tokens", nullable = false)
    private Long amountTokens;

    @Column(name = "balance_after_tokens", nullable = false)
    private Long balanceAfterTokens;

    @Column(name = "source", length = 80)
    private String source;

    @Column(name = "biz_id", length = 120)
    private String bizId;
}
