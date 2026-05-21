/**
 * Prompt 模板实体。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.core.prompt;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Prompt 模板，支持版本管理和变量注入。 */
@Getter
@Setter
@Entity
@Table(
        name = "ai_prompt_template",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"name", "version"})})
public class PromptTemplate extends BaseEntity {

    /** 模板名称（唯一标识） */
    @Column(nullable = false, length = 128)
    private String name;

    /** 版本号 */
    @Column(nullable = false)
    private Integer version = 1;

    /** 模板内容（支持 ${variable} 占位符） */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 描述 */
    @Column(length = 512)
    private String description;

    /** 变量列表（JSON 数组，如 ["name","context"]） */
    @Column(columnDefinition = "TEXT")
    private String variables;

    /** 是否为当前激活版本 */
    @Column(nullable = false)
    private Boolean active = true;

    /** 分类标签 */
    @Column(length = 64)
    private String category;
}
