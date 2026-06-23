package com.xuejiai.aaf.module.ai.persona.outfit.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 装扮商城条目。 */
@Getter
@Setter
@Entity
@Table(name = "avatar_outfit")
@SQLDelete(
        sql =
                "UPDATE avatar_outfit SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AvatarOutfit extends BaseEntity {

    @Column(name = "code", nullable = false, length = 100, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** AVATAR/OUTFIT（v0.1 仅此两类） */
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "asset_url", nullable = false, length = 1000)
    private String assetUrl;

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    /** COMMON/RARE/EPIC/LEGENDARY */
    @Column(name = "rarity", nullable = false, length = 20)
    private String rarity = "COMMON";

    /** DEFAULT/PURCHASE/REWARD/REDEEM/VIP */
    @Column(name = "unlock_condition", nullable = false, length = 100)
    private String unlockCondition = "DEFAULT";

    @Column(name = "entitlement_code", length = 50)
    private String entitlementCode;

    /** 商城价（积分），0 或 NULL=不出售 */
    @Column(name = "price")
    private Long price;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
