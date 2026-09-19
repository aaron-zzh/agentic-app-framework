package com.xuejiai.aaf.module.ai.aigc.project.api;

/** 将项目文档正文固化到不可变 ObjectVersion 候选。 */
public record AigcDocumentCandidateCommand(
        Long projectId,
        Long objectId,
        Integer expectedProjectVersion,
        Long expectedGraphRevision,
        String title,
        String content,
        String format,
        String summary,
        String idempotencyKey) {}
