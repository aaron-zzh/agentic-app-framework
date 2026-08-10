package com.xuejiai.aaf.module.ai.aigc.configuration.domain;

import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.org.OrgIgnore;
import com.xuejiai.aaf.module.ai.aigc.configuration.enums.AigcConfigStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 行业扩展实体。
 *
 * <p>平台级版本化配置，与组织无关；组织项目只引用其已发布版本。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "aigc_domain_extension")
@OrgIgnore
@SQLDelete(
        sql =
                "UPDATE aigc_domain_extension SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcDomainExtension extends BaseEntity {

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "extension_version", nullable = false, length = 32)
    private String extensionVersion;

    @Column(name = "industry", length = 64)
    private String industry;

    @Column(name = "region", length = 32)
    private String region;

    @Column(name = "language", length = 32)
    private String language;

    @Column(name = "status", nullable = false, length = 32)
    private String status = AigcConfigStatus.DRAFT.getCode();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile_schema_ext", columnDefinition = "jsonb")
    private Map<String, Object> profileSchemaExt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "object_definitions", columnDefinition = "jsonb")
    private Map<String, Object> objectDefinitions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "knowledge_requirements", columnDefinition = "jsonb")
    private Map<String, Object> knowledgeRequirements;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_sets", columnDefinition = "jsonb")
    private Map<String, Object> ruleSets;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validators", columnDefinition = "jsonb")
    private List<String> validators = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "role_recommendations", columnDefinition = "jsonb")
    private List<String> roleRecommendations = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "action_constraints", columnDefinition = "jsonb")
    private Map<String, Object> actionConstraints;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "channel_overrides", columnDefinition = "jsonb")
    private Map<String, Object> channelOverrides;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "migration_declaration", columnDefinition = "jsonb")
    private Map<String, Object> migrationDeclaration;
}
