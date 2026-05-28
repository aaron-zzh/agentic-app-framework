package com.xuejiai.aaf.module.system.file.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "sys_file")
@SQLDelete(sql = "UPDATE sys_file SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class FileRecord extends BaseEntity {

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
