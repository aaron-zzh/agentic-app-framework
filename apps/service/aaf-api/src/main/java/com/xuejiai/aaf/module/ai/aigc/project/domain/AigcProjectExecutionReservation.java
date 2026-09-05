package com.xuejiai.aaf.module.ai.aigc.project.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Project 聚合内冻结执行目标的占用与 fencing 记录。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_project_execution_reservation")
@SQLDelete(sql = "UPDATE aigc_project_execution_reservation SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcProjectExecutionReservation extends BaseEntity {

    @Column(name = "execution_submission_id", nullable = false)
    private Long executionSubmissionId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "command_object_id")
    private Long commandObjectId;

    @Column(name = "action_key", nullable = false, length = 100)
    private String actionKey;

    @Column(name = "target_graph_revision", nullable = false)
    private Long targetGraphRevision;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PREPARED";

    @Column(name = "root_execution_run_id")
    private Long rootExecutionRunId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "release_reason", length = 32)
    private String releaseReason;
}
