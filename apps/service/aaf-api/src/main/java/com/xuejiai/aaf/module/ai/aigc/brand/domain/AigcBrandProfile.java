package com.xuejiai.aaf.module.ai.aigc.brand.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 品牌/IP 稳定身份。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_brand_profile")
@SQLDelete(
        sql =
                "UPDATE aigc_brand_profile SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcBrandProfile extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "kind", nullable = false, length = 32)
    private String kind;

    @Column(name = "industry", length = 64)
    private String industry;

    @Column(name = "current_version_id")
    private Long currentVersionId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "user_id")
    private Long userId;
}
