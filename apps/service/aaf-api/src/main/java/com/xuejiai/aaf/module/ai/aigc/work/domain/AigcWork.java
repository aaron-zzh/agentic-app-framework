package com.xuejiai.aaf.module.ai.aigc.work.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 已批准且 non-stale 的不可变 DeliverableSet manifest 登记。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_work")
@SQLDelete(sql = "UPDATE aigc_work SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcWork extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "deliverable_set_object_id", nullable = false)
    private Long deliverableSetObjectId;

    @Column(name = "manifest_object_version_id", nullable = false)
    private Long manifestObjectVersionId;

    @Column(name = "collect_idempotency_key", nullable = false, length = 100)
    private String collectIdempotencyKey;

    @Column(name = "collect_request_hash", nullable = false, length = 64)
    private String collectRequestHash;

    @Column(name = "archive_idempotency_key", length = 100)
    private String archiveIdempotencyKey;

    @Column(name = "archive_request_hash", length = 64)
    private String archiveRequestHash;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "cover_media_version_id")
    private Long coverMediaVersionId;

    @Column(nullable = false, length = 32)
    private String status = "COLLECTED";

    @Column(nullable = false, length = 32)
    private String visibility = "PRIVATE";

    @Column(name = "user_id", nullable = false)
    private Long userId;
}
