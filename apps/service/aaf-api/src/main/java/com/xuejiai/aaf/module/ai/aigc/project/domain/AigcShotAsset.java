package com.xuejiai.aaf.module.ai.aigc.project.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 分镜-素材关联（素材角色：FINAL_VIDEO/FINAL_AUDIO/REFERENCE）。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_shot_asset")
public class AigcShotAsset {

    @EmbeddedId private AigcShotAssetId id;

    /** 素材角色：FINAL_VIDEO/FINAL_AUDIO/REFERENCE */
    @Column(name = "role", nullable = false, length = 20)
    private String role = "FINAL_VIDEO";
}
