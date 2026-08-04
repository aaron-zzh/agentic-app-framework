package com.xuejiai.aaf.module.ai.aigc.media.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 工作区资产分类树节点。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_asset_category")
@SQLDelete(
        sql =
                "UPDATE aigc_asset_category SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcAssetCategory extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
