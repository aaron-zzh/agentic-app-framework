package com.xuejiai.aaf.module.ai.aigc.media.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 可复用资产集合聚合根。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_asset_collection")
@SQLDelete(
        sql =
                "UPDATE aigc_asset_collection SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcAssetCollection extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "collection_type", length = 32)
    private String collectionType;

    @Column(length = 500)
    private String description;
}
