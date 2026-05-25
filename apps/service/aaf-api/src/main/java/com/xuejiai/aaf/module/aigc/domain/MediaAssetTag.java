package com.xuejiai.aaf.module.aigc.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 素材-标签关联表。 */
@Getter
@Setter
@Entity
@Table(name = "media_asset_tag")
@IdClass(MediaAssetTagId.class)
public class MediaAssetTag {

    @Id
    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Id
    @Column(name = "tag_id", nullable = false)
    private Long tagId;
}
