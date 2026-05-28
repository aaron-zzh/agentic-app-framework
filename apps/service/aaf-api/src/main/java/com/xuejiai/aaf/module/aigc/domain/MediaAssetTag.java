package com.xuejiai.aaf.module.aigc.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 素材-标签关联表。
 *
 * <p>关联 {@link MediaAsset} 与 {@link MediaTag} 的多对多中间表。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "media_asset_tag")
@IdClass(MediaAssetTagId.class)
public class MediaAssetTag {

    /** 素材 ID，关联 {@link MediaAsset} */
    @Id
    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    /** 标签 ID，关联 {@link MediaTag} */
    @Id
    @Column(name = "tag_id", nullable = false)
    private Long tagId;
}
