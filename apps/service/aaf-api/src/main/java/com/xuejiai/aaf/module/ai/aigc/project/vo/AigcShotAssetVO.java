package com.xuejiai.aaf.module.ai.aigc.project.vo;

import lombok.Data;

/** 分镜-素材关联 VO。 */
@Data
public class AigcShotAssetVO {
    private Long shotId;
    private Long assetId;

    /** 素材角色：FINAL_VIDEO/FINAL_AUDIO/REFERENCE */
    private String role;
}
