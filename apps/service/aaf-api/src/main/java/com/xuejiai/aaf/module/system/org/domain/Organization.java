package com.xuejiai.aaf.module.system.org.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.org.OrgIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 组织/租户。
 *
 * <p>组织本身没有"所属组织"概念，语义上不属于任何组织，{@code org_id} 恒为 NULL，标注 {@link OrgIgnore} 避免被 orgFilter
 * 误套用后静默查空——用户/组织管理接口按全局角色（ADMIN/SUPER_ADMIN）鉴权， 未依赖组织级隔离，不受此标注影响现有安全边界。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_organization")
@OrgIgnore
@SQLDelete(
        sql =
                "UPDATE sys_organization SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE"
                        + " id = ?")
public class Organization extends BaseEntity {

    /** 组织名称 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 组织唯一标识（URL 友好） */
    @Column(name = "slug", nullable = false, unique = true, length = 100)
    private String slug;

    /** personal 个人工作空间 / team 团队组织 */
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    /** 组织所有者用户 ID */
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /** 是否为个人工作空间 */
    public boolean isPersonal() {
        return "personal".equals(type);
    }
}
