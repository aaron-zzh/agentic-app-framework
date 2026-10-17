package com.xuejiai.aaf.module.elder.album.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.enums.elder.PhotoSourceEnum;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.reference.CrudReference;
import com.xuejiai.aaf.framework.crud.reference.ReferenceCapability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 长者相册照片，仅保存系统文件引用和拍摄元数据。 */
@Getter
@Setter
@Entity
@Table(name = "elder_photo")
@CrudReference(
        key = "createBy",
        idProperty = "createBy",
        targetResource = "system.user",
        viewField = "createBy",
        capabilities = ReferenceCapability.READ)
@CrudReference(
        key = "updateBy",
        idProperty = "updateBy",
        targetResource = "system.user",
        viewField = "updateBy",
        capabilities = ReferenceCapability.READ)
@SQLDelete(
        sql = "UPDATE elder_photo SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ElderPhoto extends BaseEntity {

    /** 所属长者。 */
    @CrudReference(
            key = "elder",
            idProperty = "elderId",
            targetResource = "elder.profile.elder",
            inputField = "elderId",
            viewField = "elder")
    @Column(name = "elder_id", nullable = false)
    private Long elderId;

    /** 拍摄设备 ID，来自已认证的设备主体。 */
    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    /** 设备生成的照片幂等编号。 */
    @Column(name = "client_photo_id", nullable = false, length = 100)
    private String clientPhotoId;

    /** 系统文件记录 ID。 */
    @Column(name = "file_id", nullable = false)
    private Long fileId;

    /** 拍摄来源。枚举 {@link PhotoSourceEnum}。 */
    @Column(name = "source", nullable = false, length = 32)
    private String source;

    /** 设备记录的拍摄时间。 */
    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    /** 文件原始名称。 */
    @Column(name = "original_name", nullable = false, length = 500)
    private String originalName;

    /** 服务端按文件头识别的 MIME 类型。 */
    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    /** 文件大小，单位字节。 */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /** 文件内容 SHA-256。 */
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    /** 运营补充的照片说明。 */
    @Column(name = "description", length = 500)
    private String description;
}
