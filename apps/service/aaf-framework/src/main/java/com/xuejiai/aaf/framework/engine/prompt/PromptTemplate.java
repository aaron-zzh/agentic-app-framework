package com.xuejiai.aaf.framework.engine.prompt;

import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 提示词稳定根对象：承载元数据、分类关系和当前已发布内容版本。 */
@Getter
@Setter
@Entity
@Table(name = "ai_prompt_template")
@SQLDelete(
        sql =
                "UPDATE ai_prompt_template SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class PromptTemplate extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PromptKind kind = PromptKind.TEMPLATE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PromptType type = PromptType.PROMPT;

    @Column(length = 512)
    private String description;

    @Column(name = "cover_url", length = 1000)
    private String coverUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PromptVisibility visibility = PromptVisibility.PRIVATE;

    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromptScope scope = PromptScope.GENERATION;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "current_version_id")
    private PromptTemplateVersion currentVersion;

    @ManyToMany
    @JoinTable(
            name = "ai_prompt_category_relation",
            joinColumns = @JoinColumn(name = "prompt_template_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<PromptCategory> categories = new LinkedHashSet<>();
}
