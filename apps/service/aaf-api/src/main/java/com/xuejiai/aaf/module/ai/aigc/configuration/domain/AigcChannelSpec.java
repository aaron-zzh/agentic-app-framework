package com.xuejiai.aaf.module.ai.aigc.configuration.domain;

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
 * 渠道规格实体。
 *
 * <p>平台级版本化配置，与组织无关；组织项目只引用其已发布版本。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "aigc_channel_spec")
@OrgIgnore
@SQLDelete(
        sql =
                "UPDATE aigc_channel_spec SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcChannelSpec extends BaseEntity {

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "spec_version", nullable = false, length = 32)
    private String specVersion;

    @Column(name = "aspect_ratio", length = 32)
    private String aspectRatio;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "max_duration_seconds")
    private Integer maxDurationSeconds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "copy_structure", columnDefinition = "jsonb")
    private Map<String, Object> copyStructure;

    @Column(name = "required_disclaimers", length = 1000)
    private String requiredDisclaimers;

    @Column(name = "export_format", length = 64)
    private String exportFormat;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "status", nullable = false, length = 32)
    private String status = AigcConfigStatus.DRAFT.getCode();
}
