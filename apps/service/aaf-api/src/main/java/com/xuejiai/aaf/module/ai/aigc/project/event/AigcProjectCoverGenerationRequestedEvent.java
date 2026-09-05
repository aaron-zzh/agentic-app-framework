package com.xuejiai.aaf.module.ai.aigc.project.event;

/** 项目请求生成封面。 */
public record AigcProjectCoverGenerationRequestedEvent(
        Long projectId,
        String prompt,
        Long expectedGraphRevision,
        String idempotencyKey) {}
