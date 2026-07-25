package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.assistant.model.HumanApproval;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** 审批建立、任务暂停、外部决定和恢复的应用边界。 */
public interface HitlCoordinatorPort {

    HumanApproval request(ApprovalCommand command);

    HumanApproval decide(DecisionCommand command);

    record ApprovalCommand(
            InvocationContext context,
            String action,
            String resource,
            String reason,
            String impact,
            String dataUsage,
            String remediation,
            Map<String, String> requestedConditions,
            boolean reversible,
            Instant at) {}

    record DecisionCommand(
            TenantId tenantId,
            String approvalId,
            HumanApproval.Status decision,
            String decidedBy,
            String reason,
            Instant at) {
        public DecisionCommand {
            Objects.requireNonNull(tenantId, "tenantId 不能为空");
            Objects.requireNonNull(approvalId, "approvalId 不能为空");
            Objects.requireNonNull(decision, "decision 不能为空");
            Objects.requireNonNull(decidedBy, "decidedBy 不能为空");
            Objects.requireNonNull(at, "at 不能为空");
            if (decision == HumanApproval.Status.PENDING) {
                throw new IllegalArgumentException("外部决定不能为 PENDING");
            }
        }
    }
}
