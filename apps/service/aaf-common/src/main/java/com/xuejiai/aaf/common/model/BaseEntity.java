package com.xuejiai.aaf.common.model;

import java.io.Serializable;
import java.time.LocalDateTime;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * 实体基类，所有 JPA 实体继承此类。
 *
 * <p>提供 id、多租户（org_id / workspace_id）、审计字段（创建/更新人和时间）、逻辑删除、备注。 逻辑删除通过 {@code @SQLRestriction}
 * 自动过滤查询，子类需加 {@code @SQLDelete} 指定删除 SQL。
 *
 * <p>子类示例：
 *
 * <pre>{@code
 * @SQLDelete(sql = "UPDATE sys_user SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
 * public class User extends BaseEntity { ... }
 *
 * }</pre>
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted = false")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "orgId", type = Long.class))
@Filter(name = "tenantFilter", condition = "org_id = :orgId")
public abstract class BaseEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 版本号（递增） */
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    /** 所属组织 ID */
    @Column(name = "org_id")
    private Long orgId;

    /** 所属工作空间 ID */
    @Column(name = "workspace_id")
    private Long workspaceId;

    @CreatedBy
    @Column(name = "create_by")
    private Long createBy;

    /** 创建者类型 */
    @Column(name = "create_by_type", length = 16)
    private String createByType;

    @CreatedDate
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @LastModifiedBy
    @Column(name = "update_by")
    private Long updateBy;

    /** 更新者类型 */
    @Column(name = "update_by_type", length = 16)
    private String updateByType;

    @LastModifiedDate
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /** 数据归属者（始终为 user.id，AI 操作时填委托者） */
    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "delete_time")
    private LocalDateTime deleteTime;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "remark")
    private String remark;
}
