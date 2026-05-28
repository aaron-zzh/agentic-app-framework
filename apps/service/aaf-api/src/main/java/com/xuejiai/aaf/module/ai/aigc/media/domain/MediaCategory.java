package com.xuejiai.aaf.module.ai.aigc.media.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 素材分类（多级树形结构）。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "media_category")
@SQLDelete(
        sql =
                "UPDATE media_category SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class MediaCategory extends BaseEntity {

    /** 分类名称 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 父分类 ID，顶级分类为 null */
    @Column(name = "parent_id")
    private Long parentId;

    /** 排序序号 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
