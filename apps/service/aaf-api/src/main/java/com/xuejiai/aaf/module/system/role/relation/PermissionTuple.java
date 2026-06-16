package com.xuejiai.aaf.module.system.role.relation;

import java.time.Instant;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** ReBAC 关系元组：object#relation@subject。 */
@Getter
@Setter
@Entity
@Table(
        name = "sys_permission_tuple",
        uniqueConstraints =
                @UniqueConstraint(
                        columnNames = {
                            "object_type",
                            "object_id",
                            "relation",
                            "subject_type",
                            "subject_id",
                            "subject_relation"
                        }))
public class PermissionTuple extends BaseEntity {

    @Column(name = "object_type", nullable = false, length = 50)
    private String objectType;

    @Column(name = "object_id", nullable = false, length = 100)
    private String objectId;

    @Column(nullable = false, length = 50)
    private String relation;

    @Column(name = "subject_type", nullable = false, length = 50)
    private String subjectType;

    @Column(name = "subject_id", nullable = false, length = 100)
    private String subjectId;

    @Column(name = "subject_relation", nullable = false, length = 50)
    private String subjectRelation = "";

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "granted_by", length = 100)
    private String grantedBy;
}
