package com.xuejiai.aaf.module.system.file.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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
 * 文件记录实体。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "sys_file")
@SQLDelete(sql = "UPDATE sys_file SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted = false")
public class FileRecord {

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

    /** 创建时绑定且不可变的实际存储配置 ID。 */
    @Column(name = "storage_config_id", nullable = false)
    private Long storageConfigId;

    /** 文件内容 SHA-256。 */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    /** 物理文件状态：ACTIVE/PENDING_DELETE/DELETE_FAILED/DELETED/ORPHAN。 */
    @Column(name = "storage_status", nullable = false, length = 32)
    private String storageStatus = "ACTIVE";

    /** 允许垃圾回收器执行物理删除的最早时间。 */
    @Column(name = "delete_after")
    private LocalDateTime deleteAfter;

    /** 文件唯一标识（存储 key） */
    @Column(name = "file_key", nullable = false, unique = true, length = 500)
    private String key;

    /** 原始文件名 */
    @Column(name = "original_name", nullable = false, length = 500)
    private String originalName;

    /** MIME 类型 */
    @Column(name = "mime_type", length = 200)
    private String mimeType;

    /** 文件大小（字节） */
    @Column(name = "size", nullable = false)
    private Long size;

    /** 存储路径 */
    @Column(name = "storage_path", length = 1000)
    private String storagePath;

    /** 上传者 ID */
    @Column(name = "uploader_id")
    private Long uploaderId;
}
