package com.xuejiai.aaf.module.ai.aigc.image.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 生成参数模板实体。
 *
 * <p>用户可保存常用的生成参数组合为模板，支持公开分享。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "generation_template")
@SQLDelete(
        sql =
                "UPDATE generation_template SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class GenerationTemplate extends BaseEntity {

    /** 模板名称 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 模板类型：IMAGE / VIDEO / COPYWRITING */
    @Column(name = "type", length = 30)
    private String type = "IMAGE";

    /** 模板分类 */
    @Column(name = "category", length = 50)
    private String category;

    /** 正向提示词 */
    @Column(name = "prompt", nullable = false, columnDefinition = "TEXT")
    private String prompt;

    /** 反向提示词 */
    @Column(name = "negative_prompt", columnDefinition = "TEXT")
    private String negativePrompt;

    /** 模型名称 */
    @Column(name = "model", length = 100)
    private String model;

    /** 生成宽度（像素） */
    @Column(name = "width")
    private Integer width;

    /** 生成高度（像素） */
    @Column(name = "height")
    private Integer height;

    /** 推理步数 */
    @Column(name = "steps")
    private Integer steps;

    /** 随机种子 */
    @Column(name = "seed")
    private Long seed;

    /** 是否公开 */
    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = false;

    /** 使用次数 */
    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;

    /** 使用场景：GENERATION=单次生成面板，PROJECT=项目级提示词 */
    @Column(name = "scope", nullable = false, length = 20)
    private String scope = "GENERATION";

    /** 所属用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 增加使用计数。 */
    public void incrementUsage() {
        this.usageCount++;
    }
}
