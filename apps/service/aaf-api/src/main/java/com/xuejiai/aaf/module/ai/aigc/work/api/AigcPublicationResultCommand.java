package com.xuejiai.aaf.module.ai.aigc.work.api;

import java.util.Objects;

public record AigcPublicationResultCommand(
        Long workId,
        Long publicationId,
        String status,
        String externalId,
        String externalUrl,
        String responseJson) {

    public AigcPublicationResultCommand {
        Objects.requireNonNull(workId, "workId 不能为空");
        Objects.requireNonNull(publicationId, "publicationId 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        if (status.isBlank()) {
            throw new IllegalArgumentException("status 不能为空白");
        }
    }
}
