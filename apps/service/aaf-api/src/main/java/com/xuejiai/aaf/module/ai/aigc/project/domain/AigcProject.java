package com.xuejiai.aaf.module.ai.aigc.project.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectCoverStatus;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectLifecycle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** AIGC 唯一项目聚合根。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_project")
@SQLDelete(
        sql =
                "UPDATE aigc_project SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcProject extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "project_type_code", nullable = false, length = 64)
    private String projectTypeCode;

    @Column(name = "blueprint_code", length = 64)
    private String blueprintCode;

    @Column(name = "blueprint_version", length = 32)
    private String blueprintVersion;

    @Column(name = "domain_extension_code", length = 64)
    private String domainExtensionCode;

    @Column(name = "domain_extension_version", length = 32)
    private String domainExtensionVersion;

    @Column(name = "production_mode", nullable = false, length = 32)
    private String productionMode;

    @Column(name = "generation_mode", nullable = false, length = 32)
    private String generationMode = "manual";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AigcProjectLifecycle status = AigcProjectLifecycle.CONFIGURING;

    @Column(columnDefinition = "TEXT")
    private String brief;

    @Column(columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "cover_media_version_id")
    private Long coverMediaVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "cover_status", nullable = false, length = 20)
    private AigcProjectCoverStatus coverStatus = AigcProjectCoverStatus.NONE;

    @Column(name = "cover_execution_run_id")
    private Long coverExecutionRunId;

    @Column(name = "config_snapshot_id")
    private Long configSnapshotId;

    @Column(name = "graph_revision", nullable = false)
    private Integer graphRevision = 1;

    @Column(name = "primary_brand_profile_id")
    private Long primaryBrandProfileId;

    @Column(name = "assistant_id")
    private Long assistantId;

    @Column(name = "budget_limit", precision = 12, scale = 2)
    private BigDecimal budgetLimit;

    @Column(name = "cost_used", nullable = false, precision = 12, scale = 2)
    private BigDecimal costUsed = BigDecimal.ZERO;

    @Column(name = "last_active_time")
    private LocalDateTime lastActiveTime;

    @Column(name = "lifecycle_idempotency_key", length = 100)
    private String lifecycleIdempotencyKey;

    @Column(name = "lifecycle_request_hash", length = 64)
    private String lifecycleRequestHash;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}
