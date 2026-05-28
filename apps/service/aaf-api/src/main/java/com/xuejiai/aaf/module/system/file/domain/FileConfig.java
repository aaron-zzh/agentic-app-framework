package com.xuejiai.aaf.module.system.file.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 文件存储配置实体。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_file_config")
@SQLDelete(
        sql =
                "UPDATE sys_file_config SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class FileConfig extends BaseEntity {

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
