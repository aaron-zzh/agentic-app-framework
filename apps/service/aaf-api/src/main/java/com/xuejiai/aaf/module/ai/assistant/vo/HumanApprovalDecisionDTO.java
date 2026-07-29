package com.xuejiai.aaf.module.ai.assistant.vo;

import com.xuejiai.aaf.framework.intelligent.assistant.model.HumanApproval;

import jakarta.validation.constraints.NotNull;

public record HumanApprovalDecisionDTO(@NotNull HumanApproval.Status decision, String reason) {}
