package com.xuejiai.aaf.module.system.authorization;

import org.hibernate.annotations.Check;
import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 已发布策略的不可变版本快照。
 *
 * <p>标注 {@link com.xuejiai.aaf.framework.org.OrgIgnore}：与 {@link AccessPolicy} 同理，不套用 {@code
 * org_id} 过滤；{@code from} 工厂方法仍会复制源策略的 org/workspace/owner 字段用于审计追溯，但查询不据此过滤。
 */
@Getter
@Entity
@NoArgsConstructor
@Table(
        name = "sys_access_policy_snapshot",
        uniqueConstraints = @UniqueConstraint(columnNames = {"policy_id", "policy_version"}),
        indexes =
                @Index(
                        name = "idx_access_policy_snapshot_target",
                        columnList = "target_resource,target_action,priority"))
@Check(constraints = "lifecycle in ('SHADOW','ENFORCE') and effect in ('ALLOW','DENY','CHALLENGE')")
@SQLDelete(
        sql =
                "UPDATE sys_access_policy_snapshot SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
@com.xuejiai.aaf.framework.org.OrgIgnore
public class AccessPolicySnapshot extends BaseEntity {

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "policy_version", nullable = false)
    private Long policyVersion;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    @Column(name = "condition_json", nullable = false, columnDefinition = "TEXT")
    private String conditionJson;

    @Column(name = "fact_schema_json", nullable = false, columnDefinition = "TEXT")
    private String factSchemaJson;

    @Column(nullable = false, length = 16)
    private String effect;

    @Column(nullable = false)
    private Integer priority;

    @Column(name = "target_resource", nullable = false, length = 64)
    private String targetResource;

    @Column(name = "target_action", nullable = false, length = 64)
    private String targetAction;

    @Column(nullable = false, length = 16)
    private String lifecycle;

    public static AccessPolicySnapshot from(AccessPolicy policy, long version, String lifecycle) {
        var snapshot = new AccessPolicySnapshot();
        snapshot.setOrgId(policy.getOrgId());
        snapshot.setWorkspaceId(policy.getWorkspaceId());
        snapshot.setOwnerId(policy.getOwnerId());
        snapshot.policyId = policy.getId();
        snapshot.policyVersion = version;
        snapshot.name = policy.getName();
        snapshot.description = policy.getDescription();
        snapshot.conditionJson = policy.getConditionJson();
        snapshot.factSchemaJson = policy.getFactSchemaJson();
        snapshot.effect = policy.getEffect();
        snapshot.priority = policy.getPriority();
        snapshot.targetResource = policy.getTargetResource();
        snapshot.targetAction = policy.getTargetAction();
        snapshot.lifecycle = lifecycle;
        return snapshot;
    }
}
