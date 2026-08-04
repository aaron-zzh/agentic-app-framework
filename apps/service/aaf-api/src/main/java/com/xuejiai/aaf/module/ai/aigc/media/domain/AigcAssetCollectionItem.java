package com.xuejiai.aaf.module.ai.aigc.media.domain;

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

/** 资产集合内部成员，只能经集合聚合命令维护。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_asset_collection_item")
@SQLDelete(
        sql =
                "UPDATE aigc_asset_collection_item SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcAssetCollectionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "collection_id", nullable = false)
    private Long collectionId;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(length = 32)
    private String role;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "create_time", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false)
    private Boolean deleted = false;

    @Column(name = "delete_time")
    private LocalDateTime deleteTime;
}
