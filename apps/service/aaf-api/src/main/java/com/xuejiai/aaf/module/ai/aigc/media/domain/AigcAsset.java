package com.xuejiai.aaf.module.ai.aigc.media.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 跨项目可复用的媒体登记，不复制媒体版本或文件。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_asset")
@SQLDelete(
        sql = "UPDATE aigc_asset SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcAsset extends BaseEntity {
    @Column(name = "media_id", nullable = false)
    private Long mediaId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "scope", nullable = false, length = 32)
    private String scope = "WORKSPACE";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "copyright_info", columnDefinition = "jsonb")
    private String copyrightInfo;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}
