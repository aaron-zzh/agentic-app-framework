package com.xuejiai.aaf.module.system.notify.domain;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 系统通知。 */
@Getter
@Setter
@Entity
@Table(name = "sys_notification")
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 通知类型（如 system / task / message） */
    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    /** 关联实体类型 */
    @Column(name = "entity_type", length = 50)
    private String entityType;

    /** 关联实体 ID */
    @Column(name = "entity_id")
    private Long entityId;

    /** 是否已读 */
    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;
}
