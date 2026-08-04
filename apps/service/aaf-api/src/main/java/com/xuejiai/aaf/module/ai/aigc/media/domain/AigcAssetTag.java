package com.xuejiai.aaf.module.ai.aigc.media.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 工作区资产标签。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_asset_tag")
@SQLDelete(
        sql =
                "UPDATE aigc_asset_tag SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcAssetTag extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 20)
    private String color;

    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;
}
