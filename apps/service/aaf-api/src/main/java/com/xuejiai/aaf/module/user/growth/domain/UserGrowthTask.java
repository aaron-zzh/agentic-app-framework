package com.xuejiai.aaf.module.user.growth.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 成长任务定义。 */
@Getter
@Setter
@Entity
@Table(name = "user_growth_task")
@SQLDelete(
        sql =
                "UPDATE user_growth_task SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class UserGrowthTask extends BaseEntity {

    @Column(name = "code", nullable = false, length = 100, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "icon", length = 50)
    private String icon;

    /** ONBOARDING / DAILY / ACHIEVEMENT */
    @Column(name = "category", nullable = false, length = 30)
    private String category = "ONBOARDING";

    /** 触发事件 code（关联 sys_user_event.event_code），手动=NULL */
    @Column(name = "trigger_event", length = 100)
    private String triggerEvent;

    @Column(name = "target_count", nullable = false)
    private Integer targetCount = 1;

    @Column(name = "reward_credits", nullable = false)
    private Long rewardCredits = 0L;

    @Column(name = "reward_outfit", length = 100)
    private String rewardOutfit;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
