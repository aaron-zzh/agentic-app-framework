package com.xuejiai.aaf.module.ai.assistant.vo;

import java.time.Instant;

import com.xuejiai.aaf.framework.intelligent.assistant.model.HumanApproval;

public record HumanApprovalVO(
        String approvalId,
        String taskId,
        String action,
        String resource,
        HumanApproval.Status status,
        Instant decidedAt) {

    public static HumanApprovalVO from(HumanApproval approval) {
        return new HumanApprovalVO(
                approval.approvalId(),
                approval.invocationContext().taskId().value(),
                approval.action(),
                approval.resource(),
                approval.status(),
                approval.decidedAt());
    }
}
