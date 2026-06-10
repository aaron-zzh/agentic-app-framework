package com.xuejiai.aaf.module.ai.aigc.project.domain;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 分镜-素材关联复合主键。 */
@Getter
@Setter
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AigcShotAssetId implements Serializable {

    @Column(name = "shot_id")
    private Long shotId;

    @Column(name = "asset_id")
    private Long assetId;
}
