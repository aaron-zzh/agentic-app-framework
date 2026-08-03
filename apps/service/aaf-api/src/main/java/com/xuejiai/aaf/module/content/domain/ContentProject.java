package com.xuejiai.aaf.module.content.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.enums.content.ContentGenerationModeEnum;
import com.xuejiai.aaf.common.enums.content.ContentProductionModeEnum;
import com.xuejiai.aaf.common.enums.content.ContentProjectStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 内容项目实体。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "cs_project")
@SQLDelete(
        sql = "UPDATE cs_project SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ContentProject extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

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
    private String productionMode = ContentProductionModeEnum.STANDARD.getCode();

    @Column(name = "generation_mode", nullable = false, length = 32)
    private String generationMode = ContentGenerationModeEnum.MANUAL.getCode();

    @Column(name = "status", nullable = false, length = 32)
    private String status = ContentProjectStatusEnum.DRAFT.getCode();

    @Column(name = "brief", columnDefinition = "text")
    private String brief;

    @Column(name = "cover_url", length = 1000)
    private String coverUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "channels", columnDefinition = "jsonb")
    private List<String> channels = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> configSnapshot;

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

    @Column(name = "user_id", nullable = false)
    private Long userId;
}
