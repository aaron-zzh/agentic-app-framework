package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;

/** 可跨重启恢复的人工审批事实。 */
public record HumanApproval(
        String approvalId,
        InvocationContext invocationContext,
        String action,
        String resource,
        String reason,
        String impact,
        String dataUsage,
        String remediation,
        Map<String, String> requestedConditions,
        boolean reversible,
        Status status,
        Instant createdAt,
        Instant decidedAt,
        String decidedBy,
        String decisionReason) {

    public HumanApproval {
        approvalId = requireText(approvalId, "approvalId");
        Objects.requireNonNull(invocationContext, "invocationContext 不能为空");
        action = requireText(action, "action");
        resource = requireText(resource, "resource");
        if ("*".equals(resource)) {
            throw new IllegalArgumentException("普通审批禁止通配 resource");
        }
        reason = requireText(reason, "reason");
        impact = requireText(impact, "impact");
        dataUsage = requireText(dataUsage, "dataUsage");
        remediation = requireText(remediation, "remediation");
        requestedConditions = requestedConditions == null ? Map.of() : Map.copyOf(requestedConditions);
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
    }

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空白");
        }
        return value.trim();
    }
}
