package com.xuejiai.aaf.module.content.domain;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.enums.content.ContentConfigStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.reference.CrudReference;
import com.xuejiai.aaf.framework.crud.reference.ReferenceCapability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 品牌/IP 资料实体。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "cs_brand_profile")
@CrudReference(
        key = "createBy",
        idProperty = "createBy",
        targetResource = "system.user",
        viewField = "createBy",
        capabilities = ReferenceCapability.READ)
@CrudReference(
        key = "updateBy",
        idProperty = "updateBy",
        targetResource = "system.user",
        viewField = "updateBy",
        capabilities = ReferenceCapability.READ)
@SQLDelete(
        sql =
                "UPDATE cs_brand_profile SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ContentBrandProfile extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "kind", nullable = false, length = 32)
    private String kind;

    @Column(name = "industry", length = 64)
    private String industry;

    @Column(name = "logo_url", length = 1000)
    private String logoUrl;

    @Column(name = "positioning", length = 1000)
    private String positioning;

    @Column(name = "audience", length = 1000)
    private String audience;

    @Column(name = "tone_of_voice", length = 1000)
    private String toneOfVoice;

    @Column(name = "visual_style", length = 1000)
    private String visualStyle;

    @Column(name = "disclaimer", length = 1000)
    private String disclaimer;

    @Column(name = "forbidden_items", length = 1000)
    private String forbiddenItems;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rules", columnDefinition = "jsonb")
    private Map<String, Object> rules;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile_assets", columnDefinition = "jsonb")
    private Map<String, Object> profileAssets;

    @Column(name = "profile_version", length = 32)
    private String profileVersion;

    @Column(name = "status", nullable = false, length = 32)
    private String status = ContentConfigStatusEnum.DRAFT.getCode();

    @Column(name = "user_id")
    private Long userId;
}
