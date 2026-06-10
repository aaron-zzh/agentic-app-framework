package com.xuejiai.aaf.module.ai.aigc.project.vo;

import lombok.Data;

/** 内容-素材关联 VO。 */
@Data
public class AigcContentAssetVO {
    private Long contentId;
    private Long assetId;

    /** 素材角色：MAIN/COVER/BGM/SUBTITLE */
    private String role;
}
