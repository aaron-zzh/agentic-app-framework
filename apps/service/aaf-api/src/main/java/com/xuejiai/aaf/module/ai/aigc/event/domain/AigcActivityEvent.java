package com.xuejiai.aaf.module.ai.aigc.event.domain;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 用户级 Activity 变化通知的持久游标记录。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_activity_event")
public class AigcActivityEvent extends BaseEntity {

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "execution_run_id")
    private Long executionRunId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "media_version_id")
    private Long mediaVersionId;

    @Column(name = "object_version_id")
    private Long objectVersionId;

    @Column(name = "review_id")
    private Long reviewId;

    @Column(name = "work_id")
    private Long workId;

    @Column(name = "publication_id")
    private Long publicationId;

    @Column(name = "conversation_id")
    private Long conversationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;
}
