package com.xuejiai.aaf.module.ai.aigc.work.api;

public record AigcWorkCollectCommand(
        Long projectId,
        Long deliverableObjectId,
        Long adoptedObjectVersionId,
        Long coverMediaVersionId,
        String visibility) {}
