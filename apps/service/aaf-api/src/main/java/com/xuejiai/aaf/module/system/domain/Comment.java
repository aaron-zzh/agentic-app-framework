package com.xuejiai.aaf.module.system.domain;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

/** 评论。 */
@Getter
@Setter
@Entity
@Table(name = "sys_comment")
@SQLDelete(sql = "UPDATE sys_comment SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class Comment extends BaseEntity {

    /** 关联实体类型 */
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    /** 关联实体 ID */
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /** 评论内容 */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /** @mentions 用户 ID 列表（JSON） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mentions", columnDefinition = "jsonb")
    private String mentions;
}
