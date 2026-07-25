package com.xuejiai.aaf.framework.intelligent.automation.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** Assistant、Skill、Automation、Connector 的统一版本生命周期；明确不包含 Team。 */
public final class AutomationGovernance {
    private AutomationGovernance() {}

    public record DefinitionLifecycle(
            TenantId tenantId,
            DefinitionKind kind,
            String definitionId,
            long version,
            State state,
            Review review,
            CompatibilityImpact compatibilityImpact,
            Long rollbackVersion,
            Instant updatedAt) {
        public DefinitionLifecycle {
            Objects.requireNonNull(tenantId, "tenantId 不能为空");
            Objects.requireNonNull(kind, "kind 不能为空");
            if (definitionId == null || definitionId.isBlank() || version < 1) throw new IllegalArgumentException("定义标识或版本无效");
            Objects.requireNonNull(state, "state 不能为空");
            Objects.requireNonNull(review, "review 不能为空");
            Objects.requireNonNull(compatibilityImpact, "compatibilityImpact 不能为空");
            Objects.requireNonNull(updatedAt, "updatedAt 不能为空");
        }
    }

    public record Review(ReviewStatus status, String reviewer, String reason, Instant reviewedAt) {
        public Review {
            Objects.requireNonNull(status, "review status 不能为空");
            if (reason == null || reason.isBlank()) throw new IllegalArgumentException("审核理由不能为空");
            if (status != ReviewStatus.PENDING
                    && (reviewer == null || reviewer.isBlank() || reviewedAt == null)) {
                throw new IllegalArgumentException("已处理审核必须记录审核人和时间");
            }
        }
        public void requireApproved() {
            if (status != ReviewStatus.APPROVED) throw new IllegalStateException("定义尚未审核通过");
        }
    }

    public record OrganizationPolicy(
            TenantId tenantId,
            boolean globalStop,
            Set<String> extremeRiskActions,
            int anomalyFailureThreshold,
            String policyVersion,
            Instant updatedAt) {
        public OrganizationPolicy {
            Objects.requireNonNull(tenantId, "tenantId 不能为空");
            extremeRiskActions = Set.copyOf(Objects.requireNonNull(extremeRiskActions, "extremeRiskActions 不能为空"));
            if (anomalyFailureThreshold < 1) throw new IllegalArgumentException("异常阈值必须大于 0");
            if (policyVersion == null || policyVersion.isBlank()) throw new IllegalArgumentException("policyVersion 不能为空");
            Objects.requireNonNull(updatedAt, "updatedAt 不能为空");
        }
        public void requireUnattended(Set<String> actions) {
            if (globalStop) throw new IllegalStateException("组织已全局停用自动化");
            var prohibited = actions.stream().filter(extremeRiskActions::contains).toList();
            if (!prohibited.isEmpty()) throw new IllegalStateException("极高风险动作禁止无人值守: " + prohibited);
        }
    }

    public enum DefinitionKind { ASSISTANT, SKILL, AUTOMATION, CONNECTOR }
    public enum State { DRAFT, PUBLISHED, DEPRECATED, DISABLED }
    public enum ReviewStatus { PENDING, APPROVED, REJECTED }
    public enum CompatibilityImpact { NONE, COMPATIBLE, BREAKING }
}
