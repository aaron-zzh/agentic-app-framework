package com.xuejiai.aaf.module.aigc.domain;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 素材-标签关联复合主键。
 *
 * @author AaronZZH & Kiro
 * @see MediaAssetTag
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MediaAssetTagId implements Serializable {
    private Long assetId;
    private Long tagId;
}
