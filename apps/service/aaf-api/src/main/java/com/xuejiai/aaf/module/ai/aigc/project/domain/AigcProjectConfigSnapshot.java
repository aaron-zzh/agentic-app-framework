package com.xuejiai.aaf.module.ai.aigc.project.domain;

import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 项目物化时采用的不可变配置快照。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_project_config_snapshot")
@SQLDelete(
        sql =
                "UPDATE aigc_project_config_snapshot SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcProjectConfigSnapshot extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "revision_no", nullable = false)
    private Integer revisionNo;

    @Column(name = "project_type_version", length = 32)
    private String projectTypeVersion;

    @Column(name = "blueprint_version", length = 32)
    private String blueprintVersion;

    @Column(name = "domain_extension_version", length = 32)
    private String domainExtensionVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "channel_versions", columnDefinition = "jsonb")
    private List<Long> channelVersions = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "execution_binding_versions", columnDefinition = "jsonb")
    private List<com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcExecutionBindingVersionRef>
            executionBindingVersions = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "compatibility_result", columnDefinition = "jsonb")
    private Map<String, Object> compatibilityResult;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> snapshot;
}
