package com.xuejiai.aaf.module.content.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.enums.content.ContentObjectVersionStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 内容项目对象版本。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "cs_object_version")
@SQLDelete(
        sql =
                "UPDATE cs_object_version SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ContentObjectVersion extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "object_id", nullable = false)
    private Long objectId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "status", nullable = false, length = 32)
    private String status = ContentObjectVersionStatusEnum.CANDIDATE.getCode();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_payload", columnDefinition = "jsonb")
    private Map<String, Object> contentPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "asset_refs", columnDefinition = "jsonb")
    private List<String> assetRefs = List.of();

    @Column(name = "execution_run_id")
    private Long executionRunId;

    @Column(name = "summary", length = 500)
    private String summary;

    @Column(name = "adopted_time")
    private LocalDateTime adoptedTime;

    @Column(name = "adopted_by")
    private Long adoptedBy;

    @Column(name = "superseded_by_version_id")
    private Long supersededByVersionId;
}
