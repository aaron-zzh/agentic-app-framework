package com.xuejiai.aaf.module.billing.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 积分兑换码。 */
@Getter
@Setter
@Entity
@Table(name = "credit_redeem_code")
@SQLDelete(
        sql =
                "UPDATE credit_redeem_code SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class CreditRedeemCode extends BaseEntity {

    /** SHA-256 哈希，存储时不保留明文 */
    @Column(name = "code_hash", nullable = false, unique = true, length = 64)
    private String codeHash;

    /** 前缀展示（如 CRED-XXXXX...），用于管理界面识别 */
    @Column(name = "code_prefix", nullable = false, length = 20)
    private String codePrefix;

    /** 可兑换积分数量 */
    @Column(name = "credit_amount", nullable = false)
    private Long creditAmount;

    /** 积分批次类型：SUBSCRIPTION / TOPUP / REWARD / WEEKLY / MANUAL */
    @Column(name = "batch_type", nullable = false, length = 20)
    private String batchType = "REWARD";

    /** 状态：UNUSED / REDEEMED / EXPIRED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "UNUSED";

    /** 过期时间，null 表示永不过期 */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** 兑换者用户 ID */
    @Column(name = "redeemed_by_user_id")
    private Long redeemedByUserId;

    /** 兑换时间 */
    @Column(name = "redeemed_at")
    private LocalDateTime redeemedAt;
}
