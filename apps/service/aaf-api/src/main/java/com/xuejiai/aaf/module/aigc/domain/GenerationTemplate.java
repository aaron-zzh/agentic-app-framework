package com.xuejiai.aaf.module.aigc.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 生成参数模板实体。 */
@Getter
@Setter
@Entity
@Table(name = "generation_template")
@SQLDelete(sql = "UPDATE generation_template SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class GenerationTemplate extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "prompt", nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "negative_prompt", columnDefinition = "TEXT")
    private String negativePrompt;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "steps")
    private Integer steps;

    @Column(name = "seed")
    private Long seed;

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = false;

    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 增加使用计数。 */
    public void incrementUsage() {
        this.usageCount++;
    }
}
