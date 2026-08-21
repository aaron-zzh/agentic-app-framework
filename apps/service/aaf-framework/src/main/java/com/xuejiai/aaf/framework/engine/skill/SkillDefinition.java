package com.xuejiai.aaf.framework.engine.skill;

import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Skill 稳定身份与发现根对象；可变执行定义仅存在于 {@link SkillVersion}。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(
        name = "ai_skill_definition",
        indexes = {
            @Index(
                    name = "idx_skill_definition_scope",
                    columnList = "org_id,workspace_id,visibility"),
            @Index(name = "idx_skill_definition_owner", columnList = "owner_id,org_id,workspace_id")
        })
@SQLDelete(
        sql =
                "UPDATE ai_skill_definition SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class SkillDefinition extends BaseEntity {

    /** Role 引用的稳定业务键。 */
    @Column(nullable = false, length = 100, unique = true)
    private String code;

    /** 展示名称。 */
    @Column(nullable = false, length = 128)
    private String name;

    /** 目录和选择阶段可见的一句话摘要。 */
    @Column(nullable = false, length = 512)
    private String summary;

    /** 目录选中后展示给用户的实例输入示例；不参与 Skill 执行 prompt。 */
    @Column(name = "instance_prompt", columnDefinition = "TEXT")
    private String instancePrompt;

    /** 默认语言地区。 */
    @Column(nullable = false, length = 32)
    private String locale = "zh-CN";

    /** PRIVATE、WORKSPACE 或 PUBLIC。 */
    @Column(nullable = false, length = 16)
    private String visibility = "PRIVATE";

    /** 系统内置标识。 */
    @Column(nullable = false)
    private Boolean builtIn = false;

    /** 当前已发布、可执行的不可变版本。 */
    @Column(name = "current_version_id")
    private Long currentVersionId;

    /** 克隆或派生时的来源 Skill。 */
    @Column(name = "source_skill_id")
    private Long sourceSkillId;

    /** 用于目录发现的全局受控分类。 */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "ai_skill_category_relation",
            joinColumns = @JoinColumn(name = "skill_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    @OrderBy("sortOrder ASC, id ASC")
    private Set<SkillCategory> categories = new LinkedHashSet<>();
}
