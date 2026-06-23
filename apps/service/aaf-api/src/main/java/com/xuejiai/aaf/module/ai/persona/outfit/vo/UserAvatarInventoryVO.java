package com.xuejiai.aaf.module.ai.persona.outfit.vo;

import java.time.LocalDateTime;

import lombok.Data;

/** 用户装扮库存 VO。 */
@Data
public class UserAvatarInventoryVO {
    private Long id;
    private Long outfitId;
    private Long personaId;
    private LocalDateTime obtainedAt;
    private String obtainedSource;
    private Boolean equipped;
    private AvatarOutfitVO outfit;
}
