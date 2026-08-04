package com.xuejiai.aaf.module.ai.aigc.work.api;

public record AigcPublicationView(
        Long id,
        Long workId,
        Long channelSpecVersionId,
        String channelCode,
        String status,
        String externalId,
        String externalUrl) {}
