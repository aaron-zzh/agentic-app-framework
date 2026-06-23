package com.xuejiai.aaf.module.system.user.favorite.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 用户收藏。 */
@Getter
@Setter
@Entity
@Table(
        name = "user_favorite",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_fav_user_type_target",
                    columnNames = {"user_id", "target_type", "target_id"})
        })
@SQLDelete(
        sql =
                "UPDATE user_favorite SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class UserFavorite extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** DOC/ASSET/CONVERSATION/PROMPT/PROJECT */
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
