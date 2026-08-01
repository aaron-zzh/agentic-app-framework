package com.xuejiai.aaf.module.system.role.relation;

import java.time.Instant;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * ReBAC 关系元组：object#relation@subject。
 *
 * <p>标注 {@link com.xuejiai.aaf.framework.org.OrgIgnore}：{@code objectId}/{@code subjectId} 是跨类型
 * 字符串化标识，元组本身承担鉴权判定（见 {@code ResourceRelationService#hasPermission}），套用 org 过滤会让
 * 跨组织共享的权限判定静默失效，且该表已有自己的 object/subject 维度索引，不需要再叠加组织维度。
 */
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
@com.xuejiai.aaf.framework.org.OrgIgnore
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
