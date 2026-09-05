package com.xuejiai.aaf.module.ai.aigc.work.api;

public record AigcPublicationView(
        Long id,
        Integer version,
        Long workId,
        Long channelSpecVersionId,
        String channelCode,
        String status,
        Long retryOfPublicationId,
        Integer retryCount,
        String externalId,
        String externalUrl,
        String failureCode,
        String failureMessage) {}
