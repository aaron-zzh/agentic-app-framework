package com.xuejiai.aaf.framework.engine.prompt;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Prompt 运营分类字典。 */
@Getter
@Setter
@Entity
@Table(name = "ai_prompt_category")
@SQLDelete(
        sql =
                "UPDATE ai_prompt_category SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class PromptCategory extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private Boolean enabled = true;
}
