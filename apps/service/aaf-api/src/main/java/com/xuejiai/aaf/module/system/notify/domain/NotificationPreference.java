package com.xuejiai.aaf.module.system.log.domain.domain;

import java.time.LocalTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 用户通知偏好设置。 */
@Getter
@Setter
@Entity
@Table(name = "sys_notification_preference")
@SQLDelete(
        sql =
                "UPDATE sys_notification_preference SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class NotificationPreference extends BaseEntity {

    /** 用户 ID（唯一） */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /** 偏好配置（JSONB） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences", columnDefinition = "jsonb")
    private String preferences;

    /** 免打扰开始时间 */
    @Column(name = "quiet_start")
    private LocalTime quietStart;

    /** 免打扰结束时间 */
    @Column(name = "quiet_end")
    private LocalTime quietEnd;
}
