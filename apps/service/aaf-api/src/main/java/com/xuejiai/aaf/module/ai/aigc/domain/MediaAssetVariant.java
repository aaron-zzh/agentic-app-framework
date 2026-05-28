package com.xuejiai.aaf.module.ai.aigc.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 素材变体关联（记录原始素材与变体之间的关系）。
 *
 * <p>关联 {@link MediaAsset}。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "media_asset_variant")
@SQLDelete(
        sql =
                "UPDATE media_asset_variant SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class MediaAssetVariant extends BaseEntity {

    /** 原始素材 ID */
    @Column(name = "original_asset_id", nullable = false)
    private Long originalAssetId;

    /** 变体素材 ID */
    @Column(name = "variant_asset_id", nullable = false)
    private Long variantAssetId;

    /** 参数差异（JSON，记录与原始素材不同的生成参数） */
    @Column(name = "params_diff", columnDefinition = "JSONB")
    private String paramsDiff;
}
