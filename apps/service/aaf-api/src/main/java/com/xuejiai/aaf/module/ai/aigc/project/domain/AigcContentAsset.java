package com.xuejiai.aaf.module.ai.aigc.project.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 内容-素材关联（素材角色：MAIN/COVER/BGM/SUBTITLE）。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_content_asset")
public class AigcContentAsset {

    @EmbeddedId private AigcContentAssetId id;

    /** 素材角色：MAIN/COVER/BGM/SUBTITLE */
    @Column(name = "role", nullable = false, length = 20)
    private String role = "MAIN";
}
