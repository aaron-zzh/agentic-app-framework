package com.xuejiai.aaf.module.ai.aigc.brand.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 品牌资料版本对文档版本的内部引用。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_brand_profile_document_ref")
@SQLDelete(
        sql =
                "UPDATE aigc_brand_profile_document_ref SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcBrandProfileDocumentRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "brand_profile_version_id", nullable = false)
    private Long brandProfileVersionId;

    @Column(name = "document_version_id", nullable = false)
    private Long documentVersionId;

    @Column(name = "role", nullable = false, length = 32)
    private String role;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "create_time", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "delete_time")
    private LocalDateTime deleteTime;
}
