package com.xuejiai.aaf.module.ai.persona.outfit.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 用户装扮库存。 */
@Getter
@Setter
@Entity
@Table(
        name = "user_avatar_inventory",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_inv_user_persona_outfit",
                    columnNames = {"user_id", "persona_id", "outfit_id"})
        })
@SQLDelete(
        sql =
                "UPDATE user_avatar_inventory SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class UserAvatarInventory extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 哪个助理的装扮，NULL=全局默认 */
    @Column(name = "persona_id")
    private Long personaId;

    @Column(name = "outfit_id", nullable = false)
    private Long outfitId;

    @Column(name = "obtained_at", nullable = false)
    private LocalDateTime obtainedAt;

    /** DEFAULT/PURCHASE/REWARD/REDEEM */
    @Column(name = "obtained_source", nullable = false, length = 20)
    private String obtainedSource;

    @Column(name = "equipped", nullable = false)
    private Boolean equipped = false;
}
