package com.xuejiai.aaf.module.ai.aigc.project.domain;

import java.time.LocalDateTime;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 项目对象不可变候选与采用版本。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_object_version")
@SQLDelete(
        sql =
                "UPDATE aigc_object_version SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcObjectVersion extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "object_id", nullable = false)
    private Long objectId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(nullable = false, length = 32)
    private String status = "candidate";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_payload", columnDefinition = "jsonb")
    private Map<String, Object> contentPayload;

    @Column(name = "document_version_id")
    private Long documentVersionId;

    @Column(name = "execution_run_id")
    private Long executionRunId;

    @Column(length = 500)
    private String summary;

    @Column(name = "adopted_time")
    private LocalDateTime adoptedTime;

    @Column(name = "adopted_by")
    private Long adoptedBy;

    @Column(name = "superseded_by_version_id")
    private Long supersededByVersionId;
}
