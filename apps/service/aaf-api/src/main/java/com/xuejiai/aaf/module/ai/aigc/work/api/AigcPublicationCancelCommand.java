package com.xuejiai.aaf.module.ai.aigc.work.api;

public record AigcPublicationCancelCommand(
        Long workId,
        Long publicationId,
        Integer expectedProjectVersion,
        Integer expectedWorkVersion,
        Integer expectedPublicationVersion,
        String reason,
        String idempotencyKey) {}
