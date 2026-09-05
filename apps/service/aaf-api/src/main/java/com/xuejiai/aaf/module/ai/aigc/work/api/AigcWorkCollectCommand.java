package com.xuejiai.aaf.module.ai.aigc.work.api;

public record AigcWorkCollectCommand(
        Long projectId,
        Long deliverableSetObjectId,
        Long manifestObjectVersionId,
        Integer expectedProjectVersion,
        Long coverMediaVersionId,
        String visibility,
        String idempotencyKey) {}
