package com.xuejiai.aaf.module.ai.aigc.brand.domain;

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

/** 不可变品牌/IP 资料版本。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_brand_profile_version")
@SQLDelete(
        sql =
                "UPDATE aigc_brand_profile_version SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcBrandProfileVersion extends BaseEntity {

    @Column(name = "brand_profile_id", nullable = false)
    private Long brandProfileId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

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

    @Column(name = "status", nullable = false, length = 32)
    private String status;
}
