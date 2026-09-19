package com.xuejiai.aaf.module.ai.aigc.project.api;

/** 将已完成的纯文本正文冻结为 DocumentVersion 并登记候选版本。 */
public record AigcTextCandidateCommand(
        Long projectId,
        Long objectId,
        Integer expectedProjectVersion,
        Long expectedGraphRevision,
        String content,
        String summary,
        String idempotencyKey) {}
