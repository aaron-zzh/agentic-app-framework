package com.xuejiai.aaf.module.ai.aigc.configuration.domain;

import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.module.ai.aigc.configuration.enums.AigcConfigStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 项目类型、蓝图、领域、渠道和执行绑定的兼容版本组合。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_project_type_package")
@SQLDelete(
        sql =
                "UPDATE aigc_project_type_package SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcProjectTypePackage extends BaseEntity {

    @Column(name = "package_version", nullable = false, length = 32)
    private String packageVersion;

    @Column(name = "project_type_id", nullable = false)
    private Long projectTypeId;

    @Column(name = "blueprint_id", nullable = false)
    private Long blueprintId;

    @Column(name = "domain_extension_id")
    private Long domainExtensionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "channel_spec_ids", nullable = false, columnDefinition = "jsonb")
    private List<Long> channelSpecIds = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "execution_binding_ids", nullable = false, columnDefinition = "jsonb")
    private List<Long> executionBindingIds = List.of();

    @Column(name = "production_mode", nullable = false, length = 32)
    private String productionMode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "compatibility_result", columnDefinition = "jsonb")
    private Map<String, Object> compatibilityResult;

    @Column(name = "status", nullable = false, length = 32)
    private String status = AigcConfigStatus.DRAFT.getCode();
}
