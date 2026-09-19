package com.xuejiai.aaf.module.ai.aigc.project.api;

public enum AigcObjectReadinessState {
    SATISFIED,
    READY,
    BLOCKED_BY_DEPENDENCY,
    BLOCKED_BY_CONTEXT,
    BLOCKED_BY_POLICY,
    ACTIVE_RUN,
    PENDING_CANDIDATE,
    AWAITING_CONFIRMATION
}
