package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.time.LocalDateTime;

public record AigcReviewView(
        Long reviewObjectId,
        Integer reviewVersion,
        Long projectId,
        Long subjectObjectId,
        Long subjectObjectVersionId,
        String evidenceHash,
        String status,
        String comment,
        String staleReason,
        LocalDateTime submittedTime,
        LocalDateTime decidedTime) {}
