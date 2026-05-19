package com.xuejiai.aaf.module.system.domain;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

/** 仪表盘。 */
@Getter
@Setter
@Entity
@Table(name = "sys_dashboard")
@SQLDelete(
        sql = "UPDATE sys_dashboard SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class Dashboard extends BaseEntity {

    /** 仪表盘名称 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 描述 */
    @Column(name = "description", length = 500)
    private String description;

    /** 是否默认仪表盘 */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    /** 所有者用户 ID */
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;
}
