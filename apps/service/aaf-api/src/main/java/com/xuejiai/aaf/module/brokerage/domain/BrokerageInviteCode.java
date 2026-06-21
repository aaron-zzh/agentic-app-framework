package com.xuejiai.aaf.module.brokerage.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 分销邀请码。一人一码，可按 channel 区分推广来源。 */
@Getter
@Setter
@Entity
@Table(name = "brokerage_invite_code")
@SQLDelete(
        sql =
                "UPDATE brokerage_invite_code SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class BrokerageInviteCode extends BaseEntity {

    /** 归属联系人 contact_id */
    @Column(name = "contact_id", nullable = false)
    private Long contactId;

    /** 短码，如 AAF-X8K2 */
    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    /** 推广来源渠道，null=默认 */
    @Column(name = "channel", length = 32)
    private String channel;

    /** 被使用（绑定）次数 */
    @Column(name = "used_count", nullable = false)
    private Integer usedCount = 0;
}
