package com.xuejiai.aaf.module.system.log.domain.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 字段变更订阅。 */
@Getter
@Setter
@Entity
@Table(name = "sys_subscription")
@SQLDelete(
        sql =
                "UPDATE sys_subscription SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class Subscription extends BaseEntity {

    /** 订阅用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 订阅的实体类型 */
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    /** 订阅的实体 ID */
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /** 订阅的字段列表（JSONB，如 ["status","priority"]） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fields", columnDefinition = "jsonb")
    private String fields;

    /** 通知渠道（JSONB，如 ["in_app","email"]） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "channels", columnDefinition = "jsonb")
    private String channels;
}
