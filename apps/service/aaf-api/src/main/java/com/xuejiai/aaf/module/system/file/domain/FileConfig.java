package com.xuejiai.aaf.module.system.file.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.xuejiai.aaf.framework.org.OrgIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 文件存储配置实体。
 *
 * <p>平台级基础设施配置（存储后端选择），{@code master} 全局唯一主配置判断不区分组织，标注 {@link OrgIgnore} 避免被 orgFilter 误套用后静默查空。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "sys_file_config")
@OrgIgnore
@SQLDelete(
        sql =
                "UPDATE sys_file_config SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted = false")
public class FileConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version", nullable = false)
    private Integer version = 0;

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "workspace_id")
    private Long workspaceId;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @CreatedBy
    @Column(name = "create_by")
    private Long createBy;

    @CreatedDate
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @LastModifiedBy
    @Column(name = "update_by")
    private Long updateBy;

    @LastModifiedDate
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "delete_time")
    private LocalDateTime deleteTime;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "remark", length = 255)
    private String remark;

    /** 配置名称 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 存储类型：LOCAL / S3 / OSS */
    @Column(name = "storage_type", nullable = false, length = 20)
    private String storageType;

    /** 配置内容（JSON） */
    @Column(name = "config", columnDefinition = "TEXT")
    private String config;

    /** 是否主配置 */
    @Column(name = "master", nullable = false)
    private Boolean master = false;

    /** 状态（0 正常 / 1 禁用） */
    @Column(name = "status", nullable = false)
    private Integer status = 0;
}
