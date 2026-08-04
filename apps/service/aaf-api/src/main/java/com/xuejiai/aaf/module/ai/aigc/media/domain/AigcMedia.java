package com.xuejiai.aaf.module.ai.aigc.media.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaType;
import com.xuejiai.aaf.module.ai.aigc.media.enums.AigcMediaSourceType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 持久媒体的稳定逻辑身份。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_media")
@SQLDelete(
        sql = "UPDATE aigc_media SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcMedia extends BaseEntity {
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    private AigcMediaType mediaType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private AigcMediaSourceType sourceType;

    @Column(name = "source_execution_run_id")
    private Long sourceExecutionRunId;

    @Column(name = "source_task_id")
    private Long sourceTaskId;

    @Column(name = "current_version_id")
    private Long currentVersionId;

    @Column(name = "original_project_id")
    private Long originalProjectId;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}
