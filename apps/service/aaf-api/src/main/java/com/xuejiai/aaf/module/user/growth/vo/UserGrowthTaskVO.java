package com.xuejiai.aaf.module.user.growth.vo;

import java.time.LocalDateTime;

import lombok.Data;

/** 任务 + 用户进度合并 VO。 */
@Data
public class UserGrowthTaskVO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String icon;
    private String category;
    private String triggerEvent;
    private Integer targetCount;
    private Long rewardCredits;
    private String rewardOutfit;
    private Integer sortOrder;

    /** 当前用户进度 */
    private Integer userProgress;

    /** PENDING / COMPLETED / CLAIMED */
    private String userStatus;

    private LocalDateTime userCompletedTime;
    private LocalDateTime userClaimedTime;
}
