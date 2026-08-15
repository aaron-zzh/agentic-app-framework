package com.xuejiai.aaf.module.system.file.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.org.OrgIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "sys_file_config")
@OrgIgnore
@SQLDelete(
        sql =
                "UPDATE sys_file_config SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class FileConfig extends BaseEntity {

    /** 配置名称。 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 存储类型：LOCAL / S3 / OSS。 */
    @Column(name = "storage_type", nullable = false, length = 20)
    private String storageType;

    /** 配置内容（JSON，不保存真实凭证）。 */
    @Column(name = "config", nullable = false, columnDefinition = "TEXT")
    private String config;

    /** 是否主配置。 */
    @Column(name = "master", nullable = false)
    private Boolean master = false;

    /** 生命周期：ACTIVE / RETIRED。 */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";
}
