package com.xuejiai.aaf.module.ai.aigc.work.api;

public record AigcPublicationResultCommand(
        Long workId,
        Long publicationId,
        Integer expectedProjectVersion,
        Integer expectedPublicationVersion,
        String status,
        String externalId,
        String externalUrl,
        String failureCode,
        String failureMessage,
        String responseJson) {}
