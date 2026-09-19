package com.xuejiai.aaf.module.ai.aigc.project.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

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

    @Column(columnDefinition = "TEXT")
    private String background;

    @Column(name = "target_audience", columnDefinition = "TEXT")
    private String targetAudience;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> requirements = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> constraints = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_facts", nullable = false, columnDefinition = "jsonb")
    private List<String> keyFacts = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_messages", nullable = false, columnDefinition = "jsonb")
    private List<String> keyMessages = List.of();

    @Column(name = "style_guidance", columnDefinition = "TEXT")
    private String styleGuidance;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "success_criteria", nullable = false, columnDefinition = "jsonb")
    private List<String> successCriteria = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "delivery_requirements", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> deliveryRequirements = Map.of();

    @Column(length = 32)
    private String locale;

    @Column(name = "additional_context", columnDefinition = "TEXT")
    private String additionalContext;

    @Column(name = "project_type_code", nullable = false, length = 64)
    private String projectTypeCode;

    @Column(name = "blueprint_code", length = 64)
    private String blueprintCode;

    @Column(name = "blueprint_version", length = 32)
    private String blueprintVersion;

    @Column(name = "production_mode", nullable = false, length = 32)
    private String productionMode;

    // 控制在用户已经确定目标范围后，系统是否可以在依赖逐步满足时，自主创建后续生成任务并连续推进。
    @Column(name = "generation_mode", nullable = false, length = 32)
    private String generationMode = "manual";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AigcProjectLifecycle status = AigcProjectLifecycle.CONFIGURING;

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

    @Column(name = "conversation_id")
    private Long conversationId;

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
