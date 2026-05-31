package com.xuejiai.aaf.module.developer.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 开发者订阅实例。 */
@Getter
@Setter
@Entity
@Table(name = "developer_subscription")
@SQLDelete(
        sql =
                "UPDATE developer_subscription SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class DeveloperSubscription extends BaseEntity {

    @Column(name = "developer_id", nullable = false)
    private Long developerId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "source_id")
    private Long sourceId;
}
