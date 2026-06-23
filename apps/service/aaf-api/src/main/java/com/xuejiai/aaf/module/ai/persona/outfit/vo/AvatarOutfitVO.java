package com.xuejiai.aaf.module.ai.persona.outfit.vo;

import java.time.LocalDateTime;

import lombok.Data;

/** 装扮详情 VO。 */
@Data
public class AvatarOutfitVO {
    private Long id;
    private String code;
    private String name;
    private String type;
    private String assetUrl;
    private String thumbnailUrl;
    private String rarity;
    private String unlockCondition;
    private String entitlementCode;
    private Long price;
    private Integer sortOrder;

    /** 当前用户是否拥有 */
    private Boolean owned;

    /** 当前用户是否装备 */
    private Boolean equipped;

    private LocalDateTime createTime;
}
