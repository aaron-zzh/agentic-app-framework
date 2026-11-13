package com.xuejiai.aaf.module.ai.role;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.org.OrgIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色分类：角色库的浏览标签。
 *
 * <p>系统分类（SYSTEM）由管理员维护、所有用户可见；私有分类（PRIVATE）只有所有者可见、可改、可删。系统分类以 {@code visibility} 判断，不以 {@code
 * ownerId} 是否为空判断：管理员经 CRUD 创建时框架会把 {@code ownerId} 填为管理员。
 *
 * <p>标注 {@link OrgIgnore}：分类没有组织、工作区维度，{@code org_id}、{@code workspace_id} 恒为 NULL。助理加载与角色路由不读取分类。
 */
@Getter
@Setter
@Entity
@Table(name = "ai_role_category")
@OrgIgnore
@SQLDelete(
        sql =
                "UPDATE ai_role_category SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AiRoleCategory extends BaseEntity {

    /** 系统分类：管理员维护，所有用户可见。 */
    public static final String VISIBILITY_SYSTEM = "SYSTEM";

    /** 私有分类：仅所有者可见、可改、可删。 */
    public static final String VISIBILITY_PRIVATE = "PRIVATE";

    /** 稳定业务码；系统分类必填，私有分类为空。 */
    @Column(length = 64)
    private String code;

    /** 显示名称。 */
    @Column(nullable = false, length = 64)
    private String name;

    /** 排序值，升序。 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 100;

    /** 可见性：SYSTEM / PRIVATE，创建后不可修改。 */
    @Column(nullable = false, length = 16)
    private String visibility;

    /** 是否系统分类。 */
    public boolean systemCategory() {
        return VISIBILITY_SYSTEM.equals(visibility);
    }

    /** 是否为指定用户的私有分类。 */
    public boolean privateOf(Long userId) {
        return VISIBILITY_PRIVATE.equals(visibility)
                && userId != null
                && userId.equals(getOwnerId());
    }
}
