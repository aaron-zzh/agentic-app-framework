package com.xuejiai.aaf.module.developer.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 开发者 Token 兑换码。 */
@Getter
@Setter
@Entity
@Table(name = "developer_redeem_code")
@SQLDelete(
        sql =
                "UPDATE developer_redeem_code SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class DeveloperRedeemCode extends BaseEntity {

    @Column(name = "code_hash", nullable = false, unique = true, length = 64)
    private String codeHash;

    @Column(name = "code_prefix", nullable = false, length = 20)
    private String codePrefix;

    @Column(name = "token_amount", nullable = false)
    private Long tokenAmount;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "UNUSED";

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "redeemed_by_developer_id")
    private Long redeemedByDeveloperId;

    @Column(name = "redeemed_at")
    private LocalDateTime redeemedAt;
}
