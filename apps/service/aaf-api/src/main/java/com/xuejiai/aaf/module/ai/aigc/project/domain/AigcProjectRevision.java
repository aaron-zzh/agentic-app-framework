package com.xuejiai.aaf.module.ai.aigc.project.domain;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 项目对象图谱不可变逻辑修订。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_project_revision")
public class AigcProjectRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "revision_no", nullable = false)
    private Integer revisionNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changed_object_ids", nullable = false, columnDefinition = "jsonb")
    private List<Long> changedObjectIds = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changed_relation_ids", nullable = false, columnDefinition = "jsonb")
    private List<Long> changedRelationIds = List.of();

    @Column(name = "actor_type", nullable = false, length = 16)
    private String actorType;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "source_execution_run_id")
    private Long sourceExecutionRunId;

    @Column(length = 500)
    private String summary;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime = LocalDateTime.now();
}
