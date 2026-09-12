package com.xuejiai.aaf.module.system.authorization;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * ABAC 访问策略的可编辑定义；运行时只读取已发布快照。
 *
 * <p>标注 {@link com.xuejiai.aaf.framework.org.OrgIgnore}：策略引擎是平台级基础设施，租户边界由自身的 {@code tenantId}（见
 * {@code AuthorizationAudit}）与策略条件表达，不套用 {@code org_id} 过滤。
 */
@Getter
@Setter
@Entity
@Table(
        name = "sys_access_policy",
        indexes =
                @Index(
                        name = "idx_sys_access_policy_runtime",
                        columnList = "lifecycle,target_resource,target_action,priority"),
        check =
                @CheckConstraint(
                        constraint =
                                "lifecycle in ('DRAFT','SHADOW','ENFORCE','DISABLED') and effect in ('ALLOW','DENY','CHALLENGE')"))
@com.xuejiai.aaf.framework.org.OrgIgnore
public class AccessPolicy extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    @Column(name = "condition_json", nullable = false, columnDefinition = "TEXT")
    private String conditionJson = "{}";

    @Column(name = "fact_schema_json", nullable = false, columnDefinition = "TEXT")
    private String factSchemaJson = "{}";

    @Column(nullable = false, length = 16)
    private String effect = "ALLOW";

    @Column(nullable = false)
    private Integer priority = 100;

    @Column(name = "target_resource", nullable = false, length = 64)
    private String targetResource;

    @Column(name = "target_action", nullable = false, length = 64)
    private String targetAction;

    @Column(nullable = false, length = 16)
    private String lifecycle = "DRAFT";

    @Column(name = "published_version", nullable = false)
    private Long publishedVersion = 0L;
}
