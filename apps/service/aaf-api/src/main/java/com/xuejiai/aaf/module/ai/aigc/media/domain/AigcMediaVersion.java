package com.xuejiai.aaf.module.ai.aigc.media.domain;

import java.math.BigDecimal;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 媒体的不可变物理文件版本。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_media_version")
@SQLDelete(
        sql =
                "UPDATE aigc_media_version SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcMediaVersion extends BaseEntity {
    @Column(name = "media_id", nullable = false)
    private Long mediaId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "thumbnail_file_id")
    private Long thumbnailFileId;

    @Column(name = "mime_type", length = 200)
    private String mimeType;

    @Column(name = "size")
    private Long size;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "duration", precision = 12, scale = 3)
    private BigDecimal duration;

    @Column(name = "frame_rate", precision = 8, scale = 3)
    private BigDecimal frameRate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "generation_info", columnDefinition = "jsonb")
    private String generationInfo;

    @Column(name = "checksum", length = 64)
    private String checksum;
}
