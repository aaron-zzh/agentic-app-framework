package com.xuejiai.aaf.module.ai.aigc.project.domain;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 内容-素材关联复合主键。 */
@Getter
@Setter
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AigcContentAssetId implements Serializable {

    @Column(name = "content_id")
    private Long contentId;

    @Column(name = "asset_id")
    private Long assetId;
}
