package com.xuejiai.aaf.module.system.profile.domain;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 用户画像主表（聚合摘要）。 */
@Getter
@Setter
@Entity
@Table(name = "user_profile")
public class UserProfile extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "lifecycle_stage", nullable = false, length = 32)
    private String lifecycleStage = "new";

    @Column(name = "ai_summary")
    private String aiSummary;

    @Column(name = "last_analyzed_at")
    private LocalDateTime lastAnalyzedAt;
}
